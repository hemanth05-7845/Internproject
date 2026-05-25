package com.mafia.service;

import com.mafia.entity.Player;
import com.mafia.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoleAssignmentService {

    private final PlayerRepository playerRepository;

    public RoleAssignmentService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void assignRoles(List<Player> players) {
        List<String> roles = buildRoleList(players.size());
        Collections.shuffle(roles);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setRole(roles.get(i));
            playerRepository.save(players.get(i));
        }
    }

    private List<String> buildRoleList(int n) {
        int mafiaCount = Math.max(1, n / 3);
        int policeCount = 1;
        int doctorCount = (n >= 4) ? Math.max(1, n / 4) : 0;
        List<String> roles = new ArrayList<>();
        for (int i = 0; i < mafiaCount; i++) roles.add("MAFIA");
        for (int i = 0; i < policeCount; i++) roles.add("POLICE");
        for (int i = 0; i < doctorCount; i++) roles.add("DOCTOR");
        if (roles.size() < n) roles.add("SOLDIER");
        while (roles.size() < n) roles.add("VILLAGER");
        return roles;
    }
}