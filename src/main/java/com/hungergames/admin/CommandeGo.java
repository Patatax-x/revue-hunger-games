package com.hungergames.admin;

import com.hungergames.classe.Classe;
import com.hungergames.classe.ClasseManager;
import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.game.CommandeEnd;
import com.hungergames.game.FreezeManager;
import com.hungergames.game.KillTracker;
import com.hungergames.game.MatchScheduler;
import com.hungergames.lobby.LobbyManager;
import com.hungergames.loot.ChestManager;
import com.hungergames.loot.ConfigMap;
import com.hungergames.loot.LootTables;
import com.hungergames.map.BorderManager;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandeGo implements CommandExecutor, Listener, TabCompleter {
    private static final String TITRE_GUI = Messages.get("commandego.choisir_la_map");

    private boolean modeTestSolo = false;

    private final LobbyManager lobbyManager;
    private final MapManager mapManager;
    private final ChestManager chestManager;
    private final FreezeManager freezeManager;
    private final RoleManager roleManager;
    private final KillTracker killTracker;
    private final BorderManager borderManager;
    private final MatchScheduler matchScheduler;
    private final CommandeEnd commandeEnd;
    private final MatchAdminKit matchAdminKit;
    private final ClasseManager classeManager;
    private final EquipeManager equipeManager;
    private final AdminKitManager adminKitManager;

    public CommandeGo(LobbyManager lobbyManager, MapManager mapManager, ChestManager chestManager, FreezeManager freezeManager, RoleManager roleManager, KillTracker killTracker, BorderManager borderManager, MatchScheduler matchScheduler, CommandeEnd commandeEnd, MatchAdminKit matchAdminKit, ClasseManager classeManager, EquipeManager equipeManager, AdminKitManager adminKitManager) {
        this.lobbyManager = lobbyManager;
        this.mapManager = mapManager;
        this.chestManager = chestManager;
        this.freezeManager = freezeManager;
        this.roleManager = roleManager;
        this.killTracker = killTracker;
        this.borderManager = borderManager;
        this.matchScheduler = matchScheduler;
        this.commandeEnd = commandeEnd;
        this.matchAdminKit = matchAdminKit;
        this.classeManager = classeManager;
        this.equipeManager = equipeManager;
        this.adminKitManager = adminKitManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("test")) {
            modeTestSolo = !modeTestSolo;
            player.sendMessage(modeTestSolo
                    ? Messages.get("commandego.mode_test_active_go_accepte_maintenant")
                    : Messages.get("commandego.mode_test_desactive_go_redemande_au", "reglages", Reglages.entier("match.min-players")));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("test_duo")) {
            lancerTestDuo(player);
            return true;
        }

        ouvrirMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("test", "test_duo");
        }
        return Collections.emptyList();
    }

    private void lancerTestDuo(Player player) {
        if (killTracker.estPartieDemarree()) {
            player.sendMessage(Messages.get("commandego.une_partie_est_deja_en_cours"));
            return;
        }

        if (commandeEnd.isFinDePartieEnCours()) {
            player.sendMessage(Messages.get("commandego.la_cloture_de_la_partie_precedente"));
            return;
        }

        if (freezeManager.compteAReboursEnCours()) {
            player.sendMessage(Messages.get("commandego.erreur_le_compte_rebours_de_lancement"));
            return;
        }

        List<Player> joueurs = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (joueurs.size() != 2) {
            player.sendMessage(Messages.get("commandego.go_test_duo_est_utilisable_que", "joueurs", joueurs.size()));
            return;
        }

        for (Player p : joueurs) {
            if (roleManager.estExclu(p)) {
                player.sendMessage(Messages.get("commandego.impossible_le_role_admin_ou_afk", "joueur", p.getName()));
                player.sendMessage(Messages.get("commandego.fais_role_remove_admin_ou_afk", "joueur", p.getName()));
                return;
            }
        }

        for (Player p : joueurs) {
            equipeManager.nettoyer(p);
            if (equipeManager.aUneEquipe(p)) {
                equipeManager.quitterEquipe(p);
            }
        }
        for (Player p : joueurs) {
            equipeManager.formerEquipeSolo(p);
        }

        for (Player p : joueurs) {
            if (classeManager.getClasse(p).isEmpty()) {
                Classe[] classes = Classe.values();
                Classe tiree = classes[(int) (Math.random() * classes.length)];
                classeManager.assignerClasse(p, tiree);
                p.sendMessage(Messages.get("commandego.test_duo_classe_attribuee_automatiquement", "tiree", tiree.getDisplayName()));
            }
        }

        if (!lobbyManager.isFermetureActive()) {
            lobbyManager.activerFermeture();
        }

        for (Player p : joueurs) {
            Player adversaire = joueurs.stream().filter(autre -> !autre.equals(p)).findFirst().orElse(null);
            p.sendMessage(Messages.get("commandego.mode_test_duo_equipes_solo_pretes", "adversaire", (adversaire != null ? adversaire.getName() : "?")));
        }
        player.sendMessage(Messages.get("commandego.choisis_la_map"));

        ouvrirMenu(player);
    }

    public void ouvrirMenu(Player player) {
        if (!lobbyManager.isFermetureActive()) {
            player.sendMessage(Messages.get("commandego.erreur_go_est_utilisable_qu_une"));
            return;
        }

        if (killTracker.estPartieDemarree()) {
            player.sendMessage(Messages.get("commandego.erreur_une_partie_est_deja_en"));
            return;
        }

        if (freezeManager.compteAReboursEnCours()) {
            player.sendMessage(Messages.get("commandego.erreur_le_compte_rebours_de_lancement"));
            return;
        }

        if (commandeEnd.isFinDePartieEnCours()) {
            player.sendMessage(Messages.get("commandego.erreur_la_cloture_de_la_partie"));
            return;
        }

        List<String> manquants = lobbyManager.joueursIncomplets();
        if (!manquants.isEmpty()) {
            player.sendMessage(Messages.get("commandego.erreur_tous_les_joueurs_ont_pas"));
            player.sendMessage(Messages.get("commandego.en_attente_de", "manquants", String.join(Messages.get("commun.separateur_liste"), manquants)));
            return;
        }

        int joueursEligibles = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!roleManager.estExclu(p)) {
                joueursEligibles++;
            }
        }
        int minimumRequis = modeTestSolo ? 1 : Reglages.entier("match.min-players");
        if (joueursEligibles < minimumRequis) {
            player.sendMessage(Messages.get("commandego.erreur_il_faut_au_moins_joueur", "minimumrequis", minimumRequis, "joueurseligibles", joueursEligibles, "minimumrequis2", minimumRequis));
            return;
        }

        int minimumEquipesRequis = modeTestSolo ? 1 : Reglages.entier("match.min-teams");
        int nombreEquipes = equipeManager.getEquipesEnLigne().size();
        if (nombreEquipes < minimumEquipesRequis) {
            player.sendMessage(Messages.get("commandego.erreur_il_faut_au_moins_equipes", "minimumequipesrequis", minimumEquipesRequis, "nombreequipes", nombreEquipes));
            return;
        }

        List<GameMap> maps = GameMap.values();
        Inventory menu = Bukkit.createInventory(null, Menus.tailleMenu(maps.size()), TITRE_GUI);
        int[] emplacements = Menus.emplacementsCentres(maps.size());

        for (int i = 0; i < emplacements.length; i++) {
            GameMap map = maps.get(i);
            menu.setItem(emplacements[i], creerItem(map.getIcone(), map, mapManager.getMapActuelle() == map));
        }

        player.openInventory(menu);
    }

    private ItemStack creerItem(Material material, GameMap map, boolean active) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            boolean nonChargee = Bukkit.getWorld(map.getCle()) == null;

            meta.setDisplayName(map.getAffichage() + (active ? Messages.get("commandego.active") : "") + (nonChargee ? Messages.get("commandego.non_chargee") : ""));
            meta.setLore(nonChargee
                    ? List.of(Messages.get("commandego.le_monde_de_cette_map_est"), Messages.get("commandego.verifie_le_dossier_et_config_yml", "cle", map.getCle()))
                    : List.of(Messages.get("commandego.clique_pour_demarrer_sur_cette_map")));
            if (active) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITRE_GUI.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();

        List<GameMap> maps = GameMap.values();
        int[] emplacements = Menus.emplacementsCentres(maps.size());
        for (int i = 0; i < emplacements.length; i++) {
            if (emplacements[i] == rawSlot) {
                player.closeInventory();
                lancerMap(player, maps.get(i));
                return;
            }
        }
    }

    private void lancerMap(Player player, GameMap map) {
        if (freezeManager.compteAReboursEnCours()) {
            player.sendMessage(Messages.get("commandego.erreur_le_compte_rebours_de_lancement"));
            return;
        }

        mapManager.demarrerMap(player, map, () -> {
            World monde = Bukkit.getWorld(map.getCle());

            if (monde == null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setInvulnerable(false);
                }
                return;
            }

            for (Item item : monde.getEntitiesByClass(Item.class)) {
                item.remove();
            }

            chestManager.regenererCoffres(monde, map);

            List<Player> joueurs = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!roleManager.estExclu(p)) {
                    joueurs.add(p);
                }
            }

            freezeManager.demarrerCompteARebours(joueurs, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setInvulnerable(false);

                    p.setGameMode(GameMode.SURVIVAL);
                }

                monde.setPVP(true);

                monde.setTime(Reglages.entier("match.start-time"));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.removePotionEffect(PotionEffectType.SATURATION);
                    p.setFoodLevel(20);
                    p.setSaturation(5.0f);
                    p.setExhaustion(0.0f);
                }

                classeManager.reinitialiserPourNouvellePartie();
                LootTables.reinitialiserPourNouvellePartie();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.getInventory().clear();

                    if (roleManager.estAdmin(p)) {
                        adminKitManager.donnerKit(p);
                        continue;
                    }

                    if (roleManager.estExclu(p)) {
                        continue;
                    }

                    classeManager.getClasse(p).ifPresent(classe -> {
                        p.getInventory().addItem(classeManager.creerLivre(classe, p));
                        p.getInventory().addItem(classeManager.creerItemActif(classe, p));
                    });

                }

                killTracker.demarrerPartie();

                ConfigMap config = chestManager.getConfig(map);
                borderManager.demarrer(monde, map, config);
                matchScheduler.demarrer(monde, map, config, commandeEnd::finDePartie);
                matchAdminKit.donnerKit(monde, map);

                Bukkit.broadcastMessage(Messages.get("commandego.que_les_hunger_games_commencent"));
            });
        });
    }
}
