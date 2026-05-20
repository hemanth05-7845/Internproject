from pydantic import BaseModel, Field


class JoinRequest(BaseModel):
    username: str = Field(min_length=2, max_length=40)


class CreateRoomRequest(BaseModel):
    room_name: str = Field(min_length=2, max_length=60)


class JoinRoomRequest(BaseModel):
    room_code: str = Field(min_length=6, max_length=6)


class LeaveRoomRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)


class StartGameRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)


class MessageRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)
    message: str = Field(min_length=1, max_length=300)


class VoteRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)
    target_player: str = Field(min_length=2, max_length=50)


class NightKillRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)
    target_player: str = Field(min_length=2, max_length=50)


class PoliceGuessRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)
    suspect_player: str = Field(min_length=2, max_length=50)


class DoctorSaveRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)
    saved_player: str = Field(min_length=2, max_length=50)


class AdvancePhaseRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)


class ResolveVotingRequest(BaseModel):
    room_id: str = Field(min_length=3, max_length=30)
