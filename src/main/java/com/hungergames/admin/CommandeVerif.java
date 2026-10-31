package com.hungergames.admin;

import com.hungergames.classe.ClasseManager;
import com.hungergames.config.Messages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.lobby.LobbyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

public class CommandeVerif implements CommandExecutor, TabCompleter {
    private static final long DELAI_RATTRAPAGE = 10L;

    private final JavaPlugin plugin;
    private final LobbyManager lobbyManager;
    private final MenuEquipe menuEquipe;
    private final MenuClasse menuClasse;
    private final ClasseManager classeManager;
    private final EquipeManager equipeManager;

    public CommandeVerif(JavaPlugin plugin, LobbyManager lobbyManager, MenuEquipe menuEquipe, MenuClasse menuClasse, ClasseManager classeManager, EquipeManager equipeManager) {
        this.plugin = plugin;
        this.lobbyManager = lobbyManager;
        this.menuEquipe = menuEquipe;
        this.menuClasse = menuClasse;
        this.classeManager = classeManager;
        this.equipeManager = equipeManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (lobbyManager.getEtat() != LobbyManager.EtatPartie.LOBBY) {
            sender.sendMessage(Messages.get("commandeverif.erreur_verif_est_utilisable_qu_en"));
            return true;
        }

        menuEquipe.ouvrirMenuBroadcast(true);
        envoyerRecapitulatif(sender, Messages.get("commandeverif.verif_lance"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player joueur : Bukkit.getOnlinePlayers()) {
                if (equipeManager.aUneNegociationEnCours(joueur)) continue;
                if (equipeManager.aUneDemandeEnvoyee(joueur)) continue;
                if (equipeManager.getDemandeurEnAttente(joueur) != null) continue;
                if (classeManager.getClasse(joueur).isPresent()) continue;

                if (!equipeManager.aUneEquipe(joueur)) continue;

                menuClasse.ouvrirMenuPourJoueurForce(joueur);
            }

            envoyerRecapitulatif(sender, Messages.get("commandeverif.rattrapage_effectue"));
        }, DELAI_RATTRAPAGE);

        return true;
    }

    private void envoyerRecapitulatif(CommandSender sender, String entete) {
        List<String> sansClasse = lobbyManager.joueursSansClasse();
        List<String> sansEquipe = lobbyManager.joueursSansEquipe();

        sender.sendMessage(entete);
        sender.sendMessage(sansClasse.isEmpty()
                ? Messages.get("commun.tout_le_monde_une_classe")
                : Messages.get("commun.sans_classe", "sansclasse", String.join(Messages.get("commun.separateur_liste"), sansClasse)));
        sender.sendMessage(sansEquipe.isEmpty()
                ? Messages.get("commun.tout_le_monde_une_equipe")
                : Messages.get("commun.sans_equipe", "sansequipe", String.join(Messages.get("commun.separateur_liste"), sansEquipe)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
