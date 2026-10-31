package com.hungergames.admin;

import com.hungergames.classe.ClasseManager;
import com.hungergames.config.Messages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.game.KillTracker;
import com.hungergames.game.MatchScheduler;
import com.hungergames.game.VictoryManager;
import com.hungergames.lobby.LobbyManager;
import com.hungergames.loot.ChestManager;
import com.hungergames.loot.ConfigMap;
import com.hungergames.map.BorderManager;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import com.hungergames.map.MapResetManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommandeHunger implements CommandExecutor, Listener, TabCompleter {
    private static final String TITRE_GUI = Messages.get("commandehunger.vue_ensemble");

    private final RoleManager roleManager;
    private final LobbyManager lobbyManager;
    private final EquipeManager equipeManager;
    private final ClasseManager classeManager;
    private final KillTracker killTracker;
    private final MapManager mapManager;
    private final VictoryManager victoryManager;
    private final MatchScheduler matchScheduler;
    private final BorderManager borderManager;
    private final ChestManager chestManager;

    private MapResetManager mapResetManager;

    public void setMapResetManager(MapResetManager mapResetManager) {
        this.mapResetManager = mapResetManager;
    }

    public CommandeHunger(RoleManager roleManager, LobbyManager lobbyManager, EquipeManager equipeManager,
                           ClasseManager classeManager, KillTracker killTracker, MapManager mapManager,
                           VictoryManager victoryManager, MatchScheduler matchScheduler, BorderManager borderManager,
                           ChestManager chestManager) {
        this.roleManager = roleManager;
        this.lobbyManager = lobbyManager;
        this.equipeManager = equipeManager;
        this.classeManager = classeManager;
        this.killTracker = killTracker;
        this.mapManager = mapManager;
        this.victoryManager = victoryManager;
        this.matchScheduler = matchScheduler;
        this.borderManager = borderManager;
        this.chestManager = chestManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        player.openInventory(construireVue());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    private Inventory construireVue() {
        Inventory inv = Bukkit.createInventory(null, 27, TITRE_GUI);

        LobbyManager.EtatPartie etat = lobbyManager.getEtat();
        boolean partieEnCours = killTracker.estPartieDemarree();

        inv.setItem(4, itemMode(etat, partieEnCours));

        if (partieEnCours) {
            remplirVuePartie(inv);
        } else if (etat == LobbyManager.EtatPartie.FERMETURE) {
            remplirVueFermeture(inv);
        } else if (etat == LobbyManager.EtatPartie.LOBBY) {
            remplirVueLobby(inv);
        } else {
            remplirVueOff(inv);
        }

        remplirVide(inv);
        return inv;
    }

    private void remplirVueOff(Inventory inv) {
        inv.setItem(11, Menus.creerItem(Material.PLAYER_HEAD, Messages.get("commandehunger.joueurs_connectes"), List.of(Messages.get("commandehunger.onlineplayers", "onlineplayers", Bukkit.getOnlinePlayers().size()))));
        inv.setItem(15, Menus.creerItem(Material.ARROW, Messages.get("commandehunger.prochaine_etape"), List.of(Messages.get("commandehunger.utilise_mode_puis_choisis_lobby"))));
    }

    private void remplirVueLobby(Inventory inv) {
        int total = 0;
        int avecClasse = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(p)) continue;
            total++;
            if (classeManager.getClasse(p).isPresent()) avecClasse++;
        }

        inv.setItem(10, Menus.creerItem(Material.PLAYER_HEAD, Messages.get("commandehunger.joueurs_en_jeu"), List.of(Messages.get("commandehunger.hors_admin_afk", "total", total))));
        inv.setItem(11, Menus.creerItem(Material.BOOK, Messages.get("commandehunger.classes_choisies"), List.of(Messages.get("commandehunger.avecclasse_total", "avecclasse", avecClasse, "total", total))));
        inv.setItem(12, Menus.creerItem(Material.LEAD, Messages.get("commandehunger.equipes_formees"), List.of(Messages.get("commandehunger.equipesenligne", "equipesenligne", equipeManager.getEquipesEnLigne().size()))));
        inv.setItem(14, itemRoles());
        inv.setItem(16, Menus.creerItem(Material.ARROW, Messages.get("commandehunger.prochaine_etape"), List.of(Messages.get("commandehunger.quand_tout_le_monde_est_pret"), Messages.get("commandehunger.mode_fermeture"))));
    }

    private void remplirVueFermeture(Inventory inv) {
        List<String> manquants = lobbyManager.joueursIncomplets();

        inv.setItem(10, Menus.creerItem(Material.PLAYER_HEAD, Messages.get("commandehunger.joueurs_en_attente"),
                manquants.isEmpty() ? List.of(Messages.get("commandehunger.tout_le_monde_est_pret")) : manquants.stream().map(n -> Messages.get("commandehunger.texte", "n", n)).toList()));
        inv.setItem(12, Menus.creerItem(Material.LEAD, Messages.get("commandehunger.equipes_formees"), List.of(Messages.get("commandehunger.equipesenligne", "equipesenligne", equipeManager.getEquipesEnLigne().size()))));
        inv.setItem(14, itemRoles());
        inv.setItem(16, Menus.creerItem(Material.ARROW, Messages.get("commandehunger.prochaine_etape"),
                manquants.isEmpty() ? List.of(Messages.get("commandehunger.go_pour_choisir_la_map_et")) : List.of(Messages.get("commandehunger.en_attente_des_joueurs_ci_contre"))));
        inv.setItem(22, itemEtatMaps());
    }

    private ItemStack itemEtatMaps() {
        List<String> lore = new ArrayList<>();

        for (GameMap map : GameMap.values()) {
            String etat;
            if (mapResetManager == null || !mapResetManager.sauvegardeDisponible(map)) {
                etat = Messages.get("commandehunger.pas_de_sauvegarde");
            } else if (mapResetManager.estPropre(map)) {
                etat = Messages.get("commandehunger.propre");
            } else {
                etat = Messages.get("commun.jouee_depuis_la_derniere_reinitialisation");
            }
            lore.add(Messages.get("commandehunger.valeur_etat", "valeur", map.getAffichage(), "etat", etat));
        }

        lore.add("");
        lore.add(Messages.get("commandehunger.gerer_resetmap"));

        return Menus.creerItem(Material.GRASS_BLOCK, Messages.get("commandehunger.etat_des_maps"), lore);
    }

    private void remplirVuePartie(Inventory inv) {
        GameMap map = mapManager.getMapActuelle();
        ConfigMap config = chestManager.getConfig(map);

        long largageS = matchScheduler.getSecondesAvantProchainLargage();
        double diametreBordure = borderManager.getDiametreActuel(map);
        long bordureS = borderManager.getSecondesAvantRetrecissement(map);
        long ecouleeS = victoryManager.getSecondesEcoulees();

        inv.setItem(10, Menus.creerItem(Material.FILLED_MAP, Messages.get("commandehunger.map_active"), List.of(map.getAffichage())));
        inv.setItem(11, Menus.creerItem(Material.PLAYER_HEAD, Messages.get("commandehunger.joueurs_restants"),
                List.of(Messages.get("commandehunger.joueursrestants_joueursdepart", "joueursrestants", victoryManager.getJoueursRestants(), "joueursdepart", victoryManager.getJoueursDepart()))));
        inv.setItem(12, Menus.creerItem(Material.LEAD, Messages.get("commandehunger.equipes_restantes"),
                List.of(Messages.get("commandehunger.equipesrestantes_equipesdepart", "equipesrestantes", victoryManager.getEquipesRestantes(), "equipesdepart", victoryManager.getEquipesDepart()))));
        inv.setItem(13, Menus.creerItem(Material.CLOCK, Messages.get("commandehunger.temps_de_partie"),
                List.of(Messages.get("commandehunger.formatduree_dureemaxpartiesecondes", "formatduree", formatDuree(ecouleeS), "dureemaxpartiesecondes", formatDuree(config.getDureeMaxPartieSecondes())))));
        inv.setItem(14, Menus.creerItem(Material.FIREWORK_ROCKET, Messages.get("commandehunger.prochain_largage"),
                List.of(largageS < 0 ? Messages.get("commandehunger.texte_2") : Messages.get("commandehunger.formatduree", "formatduree", formatDuree(largageS)))));

        String ligneDiametre = diametreBordure < 0
                ? Messages.get("commandehunger.monde_non_charge")
                : diametreBordure > 100000
                        ? Messages.get("commandehunger.aucune_bordure_blocs", "diametrebordure", String.format("%.0f", diametreBordure))
                        : Messages.get("commandehunger.diametre_blocs", "diametrebordure", String.format("%.0f", diametreBordure));

        inv.setItem(15, Menus.creerItem(Material.BARRIER, Messages.get("commandehunger.bordure"),
                List.of(ligneDiametre,
                        bordureS < 0 ? Messages.get("commandehunger.texte_2") : bordureS == 0 ? Messages.get("commandehunger.en_cours_de_retrecissement") : Messages.get("commandehunger.avant_retrecissement", "formatduree", formatDuree(bordureS)),
                        "", Messages.get("commandehunger.vue_lecture_seule_bordure_secondes"), Messages.get("commandehunger.pour_changer_la_vitesse"))));

        List<Map.Entry<String, Integer>> top = killTracker.getTop(3);
        List<String> loreTop = new ArrayList<>();
        if (top.isEmpty()) {
            loreTop.add(Messages.get("commandehunger.aucun_kill_pour_instant"));
        } else {
            for (int i = 0; i < top.size(); i++) {
                loreTop.add(Messages.get("commandehunger.rang_top_top2", "rang", (i + 1), "top", top.get(i).getKey(), "top2", top.get(i).getValue()));
            }
        }
        inv.setItem(16, Menus.creerItem(Material.GOLDEN_SWORD, Messages.get("commandehunger.top_kills"), loreTop));

        List<String> admins = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (roleManager.estAdmin(p)) admins.add(Messages.get("commandehunger.joueur", "joueur", p.getName()));
        }
        inv.setItem(22, Menus.creerItem(Material.SPYGLASS, Messages.get("commandehunger.admins_en_observation"), admins.isEmpty() ? List.of(Messages.get("commandehunger.aucun")) : admins));
    }

    private ItemStack itemMode(LobbyManager.EtatPartie etat, boolean partieEnCours) {
        if (partieEnCours) {
            return Menus.creerItem(Material.NETHER_STAR, Messages.get("commun.partie_en_cours"), List.of(Messages.get("commandehunger.une_partie_tourne_actuellement")));
        }
        return switch (etat) {
            case OFF -> Menus.creerItem(Material.BARRIER, Messages.get("commandehunger.mode_off"), List.of(Messages.get("commandehunger.rien_est_actif")));
            case LOBBY -> Menus.creerItem(Material.GRASS_BLOCK, Messages.get("commandehunger.mode_lobby"), List.of(Messages.get("commandehunger.choix_classe_equipe_en_cours")));
            case FERMETURE -> Menus.creerItem(Material.TNT, Messages.get("commandehunger.mode_fermeture_2"), List.of(Messages.get("commandehunger.en_attente_du_lancement_go")));
        };
    }

    private ItemStack itemRoles() {
        int admins = 0;
        int afk = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (roleManager.estAdmin(p)) admins++;
            if (roleManager.estAfk(p)) afk++;
        }

        return Menus.creerItem(Material.NAME_TAG, Messages.get("commandehunger.roles_speciaux"), List.of(
                Messages.get("commandehunger.admin", "admins", admins),
                Messages.get("commandehunger.afk", "afk", afk)
        ));
    }

    private void remplirVide(Inventory inv) {
        ItemStack filler = Menus.creerItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    private String formatDuree(long secondes) {
        long m = secondes / 60;
        long s = secondes % 60;
        return m + "m" + (s < 10 ? "0" + s : s) + "s";
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITRE_GUI.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
    }
}
