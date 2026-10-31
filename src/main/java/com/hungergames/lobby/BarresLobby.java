package com.hungergames.lobby;

import com.hungergames.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BarresLobby {
    private final Map<UUID, BossBar> barreEquipe = new HashMap<>();
    private final Map<UUID, BossBar> barreClasse = new HashMap<>();
    private final Map<UUID, BossBar> barreAttente = new HashMap<>();

    public void creerBarreEquipe(Player player) {
        UUID uuid = player.getUniqueId();
        if (barreEquipe.containsKey(uuid)) return;

        BossBar barre = Bukkit.createBossBar(Messages.get("lobbymanager.aucune_equipe"), BarColor.RED, BarStyle.SOLID);
        barre.addPlayer(player);
        barreEquipe.put(uuid, barre);
    }

    public void creerBarreClasse(Player player) {
        UUID uuid = player.getUniqueId();
        if (barreClasse.containsKey(uuid)) return;

        BossBar barre = Bukkit.createBossBar(Messages.get("lobbymanager.aucune_classe"), BarColor.YELLOW, BarStyle.SOLID);
        barre.addPlayer(player);
        barreClasse.put(uuid, barre);
    }

    public void creerBarreAttente(Player player) {
        UUID uuid = player.getUniqueId();
        if (barreAttente.containsKey(uuid)) return;

        BossBar barre = Bukkit.createBossBar(Messages.get("lobbymanager.en_attente_du_demarrage"), BarColor.GREEN, BarStyle.SOLID);
        barre.addPlayer(player);
        barreAttente.put(uuid, barre);
    }

    public void retirerBarreEquipe(Player player) {
        BossBar barre = barreEquipe.remove(player.getUniqueId());
        if (barre != null) barre.removeAll();
    }

    public void retirerBarreClasse(Player player) {
        BossBar barre = barreClasse.remove(player.getUniqueId());
        if (barre != null) barre.removeAll();
    }

    public void retirerBarreAttente(Player player) {
        BossBar barre = barreAttente.remove(player.getUniqueId());
        if (barre != null) barre.removeAll();
    }

    public void nettoyerToutesBarres(Player player) {
        retirerBarreEquipe(player);
        retirerBarreClasse(player);
        retirerBarreAttente(player);
    }
}
