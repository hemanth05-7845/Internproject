package com.mafia.service;

import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GameStateService gameStateService;

    @InjectMocks
    private RoomService roomService;

    private Room activeRoom(String id, int maxPlayers, String... players) {
        Room room = new Room("Test Room", "host", "ABC123", maxPlayers);
        room.setId(id);
        room.setStatus("ACTIVE");
        List<String> ids = new ArrayList<>(List.of(players));
        room.setPlayerIds(ids);
        return room;
    }

    @Nested
    @DisplayName("createRoom()")
    class CreateRoom {

        @Test
        @DisplayName("TestShouldCreateRoomWithUniqueCodeAndInitializeGameState")
        void createRoom() {
            when(roomRepository.findByRoomCode(anyString())).thenReturn(Optional.empty());
            Room saved = activeRoom("room-1", 12, "alice");
            when(roomRepository.save(argThat(r -> "Test Room".equals(r.getName()) && "alice".equals(r.getHostUsername())))).thenReturn(saved);

            Room result = roomService.createRoom("Test Room", "alice");
            ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(roomCaptor.capture());
            Room savedRoom = roomCaptor.getValue();
            assertThat(savedRoom.getName()).isEqualTo("Test Room");
            assertThat(savedRoom.getHostUsername()).isEqualTo("alice");
            assertThat(savedRoom.getMaxPlayers()).isEqualTo(12);

            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            assertThat(playerCaptor.getValue().getUsername()).isEqualTo("alice");
            assertThat(playerCaptor.getValue().getRoomId()).isEqualTo("room-1");

            assertThat(result).isSameAs(saved);
            verify(gameStateService).initializeGameState("room-1");
        }

        @Test
        @DisplayName("throws when all 20 code attempts are taken")
        void createRoom_exhaustedCodeAttempts_throws() {
            when(roomRepository.findByRoomCode(anyString()))
                    .thenReturn(Optional.of(activeRoom("x", 12)));

            assertThatThrownBy(() -> roomService.createRoom("Room", "alice"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not generate a unique room code");
        }

        @Test
        @DisplayName("succeeds on second code attempt when first is taken")
        void createRoom_secondAttemptSucceeds() {
            Room existing = activeRoom("existing", 12);
            Room saved = activeRoom("room-2", 12, "bob");

            when(roomRepository.findByRoomCode(anyString()))
                    .thenReturn(Optional.of(existing))
                    .thenReturn(Optional.empty());
            when(roomRepository.save(any(Room.class))).thenReturn(saved);

            Room result = roomService.createRoom("Test Room", "bob");

            assertThat(result).isSameAs(saved);
            ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(roomCaptor.capture());
            assertThat(roomCaptor.getValue().getHostUsername()).isEqualTo("bob");

            // Verify the player saved belongs to the correct room
            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            assertThat(playerCaptor.getValue().getUsername()).isEqualTo("bob");
            assertThat(playerCaptor.getValue().getRoomId()).isEqualTo("room-2");
        }
    }

    @Nested
    @DisplayName("getRoomById()")
    class GetRoomById {

        @Test
        @DisplayName("returns room when found")
        void getRoomById_found() {
            Room room = activeRoom("room-1", 12);
            when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

            assertThat(roomService.getRoomById("room-1")).isSameAs(room);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when not found")
        void getRoomById_notFound() {
            when(roomRepository.findById("bad-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.getRoomById("bad-id"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bad-id");
        }
    }

    @Nested
    @DisplayName("getRoomByCode()")
    class GetRoomByCode {

        @Test
        @DisplayName("uppercases the code before querying")
        void getRoomByCode_uppercasesInput() {
            Room room = activeRoom("room-1", 12);
            when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));

            Room result = roomService.getRoomByCode("abc123");

            assertThat(result).isSameAs(room);
            verify(roomRepository).findByRoomCode("ABC123");
        }

        @Test
        @DisplayName("throws when code not found")
        void getRoomByCode_notFound() {
            when(roomRepository.findByRoomCode("XXXXXX")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.getRoomByCode("xxxxxx"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("xxxxxx");
        }
    }

    @Nested
    @DisplayName("joinRoomByCode()")
    class JoinRoomByCode {

        @Test
        @DisplayName("delegates to addPlayerToRoom via getRoomByCode")
        void joinRoomByCode_addsPlayer() {
            Room room = activeRoom("room-1", 12, "alice");
            when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
            when(playerRepository.findByUsernameAndRoomId("bob", "room-1"))
                    .thenReturn(Optional.empty());
            when(roomRepository.save(room)).thenReturn(room);

            Room result = roomService.joinRoomByCode("abc123", "bob");

            assertThat(result).isSameAs(room);
            verify(roomRepository).save(room);
            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            assertThat(playerCaptor.getValue().getUsername()).isEqualTo("bob");
            assertThat(playerCaptor.getValue().getRoomId()).isEqualTo("room-1");
        }
    }

    @Nested
    @DisplayName("leaveRoom()")
    class LeaveRoom {

        @Test
        @DisplayName("removes player and saves room — room stays ACTIVE")
        void leaveRoom_playerRemoved_roomStaysActive() {
            Room room = activeRoom("room-1", 12, "alice", "bob");
            when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
            Player player = new Player("bob", "room-1");
            when(playerRepository.findByUsernameAndRoomId("bob", "room-1"))
                    .thenReturn(Optional.of(player));

            roomService.leaveRoom("room-1", "bob");

            assertThat(room.getPlayerIds()).containsOnly("alice");
            assertThat(room.getStatus()).isEqualTo("ACTIVE");
            verify(playerRepository).delete(player);
            verify(roomRepository).save(room);
        }

        @Test
        @DisplayName("sets room status to CLOSED when last player leaves")
        void leaveRoom_lastPlayerLeaves_roomClosed() {
            Room room = activeRoom("room-1", 12, "alice");
            when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.empty()); // player doc already gone

            roomService.leaveRoom("room-1", "alice");

            assertThat(room.getPlayerIds()).isEmpty();
            assertThat(room.getStatus()).isEqualTo("CLOSED");
            verify(roomRepository).save(room);
        }

        @Test
        @DisplayName("leaves room intact when username not in playerIds list")
        void leaveRoom_usernameNotInList_noChange() {
            Room room = activeRoom("room-1", 12, "alice");
            when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
            when(playerRepository.findByUsernameAndRoomId("ghost", "room-1"))
                    .thenReturn(Optional.empty());

            roomService.leaveRoom("room-1", "ghost");

            // alice still present, room still active (one player remaining)
            assertThat(room.getPlayerIds()).containsOnly("alice");
            assertThat(room.getStatus()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("getRoomPlayers()")
    class GetRoomPlayers {

        @Test
        @DisplayName("delegates to playerRepository.findByRoomId")
        void getRoomPlayers_returnsList() {
            List<Player> players = List.of(new Player("alice", "room-1"), new Player("bob", "room-1"));
            when(playerRepository.findByRoomId("room-1")).thenReturn(players);

            List<Player> result = roomService.getRoomPlayers("room-1");

            assertThat(result).isEqualTo(players);
        }

        @Test
        @DisplayName("returns empty list when no players")
        void getRoomPlayers_empty() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());

            assertThat(roomService.getRoomPlayers("room-1")).isEmpty();
        }
    }

    @Nested
    @DisplayName("addPlayerToRoom() — branch coverage")
    class AddPlayerToRoom {

        @Test
        @DisplayName("throws when room is not ACTIVE")
        void addPlayer_roomNotActive_throws() {
            Room room = activeRoom("room-1", 12);
            room.setStatus("CLOSED");
            when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> roomService.joinRoomByCode("ABC123", "alice"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Room is not active");
        }

        @Test
        @DisplayName("throws when room is full")
        void addPlayer_roomFull_throws() {
            Room room = activeRoom("room-1", 2, "alice", "bob"); // maxPlayers = 2, already full
            when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> roomService.joinRoomByCode("ABC123", "charlie"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Room is full");
        }

        @Test
        @DisplayName("returns existing room without saving if player already in playerIds")
        void addPlayer_alreadyInRoom_returnsEarly() {
            Room room = activeRoom("room-1", 12, "alice");
            when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));

            Room result = roomService.joinRoomByCode("ABC123", "alice");

            assertThat(result).isSameAs(room);
            // Should NOT call save or create a new Player entity
            verify(playerRepository, never()).save(any());
            verify(roomRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips Player save if player doc already exists in DB")
        void addPlayer_playerDocAlreadyExists_skipsPlayerSave() {
            Room room = activeRoom("room-1", 12); // alice NOT in playerIds
            when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.of(new Player("alice", "room-1")));
            when(roomRepository.save(room)).thenReturn(room);

            roomService.joinRoomByCode("ABC123", "alice");

            verify(playerRepository, never()).save(any());
            ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(roomCaptor.capture());
            assertThat(roomCaptor.getValue().getPlayerIds()).contains("alice");
        }

        @Test
        @DisplayName("saves new Player doc when player not in DB yet")
        void addPlayer_newPlayerDoc_savesPlayer() {
            Room room = activeRoom("room-1", 12);
            when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.empty());
            when(roomRepository.save(room)).thenReturn(room);

            roomService.joinRoomByCode("ABC123", "alice");

            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            assertThat(playerCaptor.getValue().getUsername()).isEqualTo("alice");
            assertThat(playerCaptor.getValue().getRoomId()).isEqualTo("room-1");

            ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(roomCaptor.capture());
            assertThat(roomCaptor.getValue().getPlayerIds()).contains("alice");
        }
    }
}