db = db.getSiblingDB("mafia_db");

db.players.insertMany([
  { playerId: "P1", name: "Hemanth" },
  { playerId: "P2", name: "Player2" }
]);

db.rooms.insertOne({ roomId: "demo-room", status: "ACTIVE" });

db.game_state.insertOne({ roomId: "demo-room", phase: "LOBBY", alivePlayers: 2 });

db.events.insertOne({ roomId: "demo-room", event: "Seed initialized", timestamp: new Date().toISOString() });
