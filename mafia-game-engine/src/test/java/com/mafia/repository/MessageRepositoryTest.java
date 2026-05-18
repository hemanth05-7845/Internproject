package com.mafia.repository;

import com.mafia.entity.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataMongoTest
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @AfterEach
    void cleanup() {
        messageRepository.deleteAll();
    }

    @Test
    void findByRoomIdOrderByCreatedAtDesc_returnsSorted() throws InterruptedException {
        Message m1 = new Message("room-1", "userA", "userA", "msg1");
        m1.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        
        Message m2 = new Message("room-1", "userB", "userB", "msg2");
        m2.setCreatedAt(LocalDateTime.now());
        
        Message m3 = new Message("room-2", "userA", "userA", "msg3");
        
        messageRepository.save(m1);
        messageRepository.save(m2);
        messageRepository.save(m3);

        List<Message> messages = messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1");

        assertEquals(2, messages.size());
        assertEquals("msg2", messages.get(0).getContent()); // newest first
        assertEquals("msg1", messages.get(1).getContent()); // oldest second
    }
}
