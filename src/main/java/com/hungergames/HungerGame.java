package com.hungergames;

import com.hungergames.admin.AdminKitManager;
import com.hungergames.admin.CommandeBordure;
import com.hungergames.admin.CommandeClasseHolo;
import com.hungergames.admin.CommandeConfig;
import com.hungergames.admin.CommandeEquipe;
import com.hungergames.admin.CommandeGo;
import com.hungergames.admin.CommandeHunger;
import com.hungergames.admin.CommandeLargage;
import com.hungergames.admin.CommandeLoot;
import com.hungergames.admin.CommandeMax;
import com.hungergames.admin.CommandeMode;
import com.hungergames.admin.CommandePNJ;
import com.hungergames.admin.CommandePvp;
import com.hungergames.admin.CommandeResetMap;
import com.hungergames.admin.CommandeRole;
import com.hungergames.admin.CommandeVanish;
import com.hungergames.admin.CommandeVerif;
import com.hungergames.admin.MatchAdminKit;
import com.hungergames.admin.MenuClasse;
import com.hungergames.admin.MenuEquipe;
import com.hungergames.classe.*;
import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.game.CombatTagManager;
import com.hungergames.game.CommandeEnd;
import com.hungergames.game.FreezeManager;
import com.hungergames.game.KillTracker;
import com.hungergames.game.MatchScheduler;
import com.hungergames.game.VictoryManager;
import com.hungergames.hud.MatchScoreboard;
import com.hungergames.hud.Titre;
import com.hungergames.lobby.LobbyManager;
import com.hungergames.loot.ChestCaptureTool;
import com.hungergames.loot.ChestManager;
import com.hungergames.loot.LootRegistry;
import com.hungergames.map.BorderManager;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import com.hungergames.map.MapResetManager;
import com.hungergames.map.SpawnCercleManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class HungerGame extends JavaPlugin {
    @Override
    public void onEnable() {
        Reglages.charger(this);
        Messages.charger(this);
        GameMap.charger(getConfig().getConfigurationSection("maps"), Bukkit.getWorlds().get(0).getName(), getLogger());
        Titre.initialiser(this);

        ClasseManager classeManager = new ClasseManager(this);
        getServer().getPluginManager().registerEvents(classeManager, this);

        RoleManager roleManager = new RoleManager();
        EquipeManager equipeManager = new EquipeManager(roleManager);

        roleManager.ajouterEcouteurAdminAssigne(joueur -> {
            classeManager.retirerClasse(joueur);
            equipeManager.nettoyer(joueur);
            equipeManager.quitterEquipe(joueur);
        });

        roleManager.ajouterEcouteurAfkAssigne(joueur -> {
            classeManager.retirerClasse(joueur);
            equipeManager.nettoyer(joueur);
            equipeManager.quitterEquipe(joueur);
        });

        SpawnCercleManager spawnCercleManager = new SpawnCercleManager(equipeManager, roleManager);
        MapManager mapManager = new MapManager(spawnCercleManager);
        mapManager.chargerMondes(getLogger());

        KillTracker killTracker = new KillTracker(this, equipeManager, classeManager, roleManager);
        getServer().getPluginManager().registerEvents(killTracker, this);

        FreezeManager freezeManager = new FreezeManager(this);
        getServer().getPluginManager().registerEvents(freezeManager, this);

        Defence defence = new Defence(this, classeManager, freezeManager);
        Vitesse vitesse = new Vitesse(this, classeManager, freezeManager);
        Invisible invisible = new Invisible(this, classeManager, equipeManager, freezeManager, roleManager);
        Feu feu = new Feu(this, classeManager, freezeManager);
        LoupGarou loupGarou = new LoupGarou(this, classeManager, freezeManager, killTracker, equipeManager, roleManager);

        classeManager.enregistrer(defence, vitesse, invisible, feu, loupGarou);

        getServer().getPluginManager().registerEvents(defence, this);
        getServer().getPluginManager().registerEvents(vitesse, this);
        getServer().getPluginManager().registerEvents(invisible, this);
        getServer().getPluginManager().registerEvents(feu, this);
        getServer().getPluginManager().registerEvents(loupGarou, this);

        loupGarou.supprimerChiensOrphelins();
        getServer().getPluginManager().registerEvents(equipeManager, this);

        AdminKitManager adminKitManager = new AdminKitManager();
        getServer().getPluginManager().registerEvents(adminKitManager, this);
        roleManager.ajouterEcouteurAdminAssigne(adminKitManager::donnerKit);
        roleManager.ajouterEcouteurAdminRetire(adminKitManager::retirerKit);

        CommandePNJ commandePNJ = new CommandePNJ();
        getCommand("pnj").setExecutor(commandePNJ);
        getServer().getPluginManager().registerEvents(commandePNJ, this);

        MenuClasse menuClasse = new MenuClasse(this, classeManager, roleManager);
        getCommand("classe").setExecutor(menuClasse);
        getServer().getPluginManager().registerEvents(menuClasse, this);
        menuClasse.arreterMenu();

        MenuEquipe menuEquipe = new MenuEquipe(this, equipeManager, roleManager);
        getCommand("equipe").setExecutor(new CommandeEquipe(menuEquipe, equipeManager, roleManager, killTracker));
        getServer().getPluginManager().registerEvents(menuEquipe, this);

        LobbyManager lobbyManager = new LobbyManager(this, classeManager, equipeManager, roleManager, menuClasse, menuEquipe, mapManager, killTracker, adminKitManager, freezeManager);
        getServer().getPluginManager().registerEvents(lobbyManager, this);
        classeManager.ajouterEcouteurAssignation(lobbyManager::surClasseAssignee);
        equipeManager.ajouterEcouteurFormation(lobbyManager::surEquipeFormee);

        roleManager.ajouterEcouteurAdminAssigne(joueur -> joueur.setGameMode(GameMode.CREATIVE));
        roleManager.ajouterEcouteurAdminRetire(joueur -> {
            if (lobbyManager.isLobbyActif()) {
                joueur.setGameMode(GameMode.ADVENTURE);

                lobbyManager.donnerObjetsManquants(joueur);
                lobbyManager.afficherBarresInitiales(joueur);
            }
        });

        roleManager.ajouterEcouteurAfkRetire(joueur -> {
            if (lobbyManager.isLobbyActif()) {
                joueur.setGameMode(GameMode.ADVENTURE);
                lobbyManager.donnerObjetsManquants(joueur);
                lobbyManager.afficherBarresInitiales(joueur);
            }
        });

        LootRegistry lootRegistry = new LootRegistry(this);
        ChestManager chestManager = new ChestManager(this, lootRegistry);

        getServer().getPluginManager().registerEvents(chestManager, this);

        ChestCaptureTool chestCaptureTool = new ChestCaptureTool(this, lootRegistry);
        getServer().getPluginManager().registerEvents(chestCaptureTool, this);
        getCommand("loot").setExecutor(new CommandeLoot(this, lootRegistry, chestCaptureTool));

        CombatTagManager combatTagManager = new CombatTagManager(this, killTracker, roleManager);
        getServer().getPluginManager().registerEvents(combatTagManager, this);

        BorderManager borderManager = new BorderManager(this);
        MatchScheduler matchScheduler = new MatchScheduler(this, chestManager, roleManager, killTracker);
        getCommand("bordure").setExecutor(new CommandeBordure(killTracker, mapManager, borderManager, chestManager));
        getCommand("max").setExecutor(new CommandeMax(killTracker, mapManager, borderManager));

        MapResetManager mapResetManager = new MapResetManager(this, killTracker, mapManager);
        CommandeResetMap commandeResetMap = new CommandeResetMap(mapResetManager);
        getCommand("resetmap").setExecutor(commandeResetMap);
        getServer().getPluginManager().registerEvents(commandeResetMap, this);

        killTracker.ajouterEcouteurDemarragePartie(() -> mapResetManager.marquerJouee(mapManager.getMapActuelle()));

        CommandeVanish commandeVanish = new CommandeVanish(this);
        getCommand("vanish").setExecutor(commandeVanish);
        getServer().getPluginManager().registerEvents(commandeVanish, this);

        roleManager.ajouterEcouteurAdminRetire(commandeVanish::desactiver);

        MatchAdminKit matchAdminKit = new MatchAdminKit(mapManager, chestManager, matchScheduler, roleManager, adminKitManager, commandeVanish);
        getServer().getPluginManager().registerEvents(matchAdminKit, this);
        getCommand("largage").setExecutor(new CommandeLargage(killTracker, matchAdminKit));

        lobbyManager.setMatchAdminKit(matchAdminKit);

        roleManager.ajouterEcouteurAdminAssigne(joueur -> {
            if (killTracker.estPartieDemarree()) {
                matchAdminKit.donnerKitPourJoueurPromuEnJeu(joueur);
            }
        });

        CommandeEnd commandeEnd = new CommandeEnd(this, killTracker, mapManager, chestManager, borderManager, matchScheduler, classeManager, equipeManager, lobbyManager, matchAdminKit);
        getCommand("end").setExecutor(commandeEnd);

        VictoryManager victoryManager = new VictoryManager(this, killTracker, equipeManager, roleManager, mapManager, commandeEnd);
        getServer().getPluginManager().registerEvents(victoryManager, this);
        commandeEnd.setVictoryManager(victoryManager);
        commandeEnd.setMapResetManager(mapResetManager);
        mapResetManager.setLancementOuClotureEnCours(() -> commandeEnd.isFinDePartieEnCours() || freezeManager.compteAReboursEnCours());

        new MatchScoreboard(this, mapManager, killTracker, victoryManager, matchScheduler, borderManager);

        CommandeGo commandeGo = new CommandeGo(lobbyManager, mapManager, chestManager, freezeManager, roleManager, killTracker, borderManager, matchScheduler, commandeEnd, matchAdminKit, classeManager, equipeManager, adminKitManager);
        getCommand("go").setExecutor(commandeGo);
        getServer().getPluginManager().registerEvents(commandeGo, this);

        CommandeConfig commandeConfig = new CommandeConfig(this, chestManager);
        getCommand("config").setExecutor(commandeConfig);
        getServer().getPluginManager().registerEvents(commandeConfig, this);

        CommandeMode commandeMode = new CommandeMode(lobbyManager, commandeGo, killTracker, commandeEnd, freezeManager);
        getCommand("mode").setExecutor(commandeMode);
        getServer().getPluginManager().registerEvents(commandeMode, this);

        getCommand("verif").setExecutor(new CommandeVerif(this, lobbyManager, menuEquipe, menuClasse, classeManager, equipeManager));

        CommandeHunger commandeHunger = new CommandeHunger(roleManager, lobbyManager, equipeManager, classeManager, killTracker, mapManager, victoryManager, matchScheduler, borderManager, chestManager);
        commandeHunger.setMapResetManager(mapResetManager);
        getCommand("hunger").setExecutor(commandeHunger);
        getServer().getPluginManager().registerEvents(commandeHunger, this);
        getCommand("role").setExecutor(new CommandeRole(roleManager, lobbyManager, killTracker));
        getCommand("pvp").setExecutor(new CommandePvp(mapManager));

        CommandeClasseHolo commandeClasseHolo = new CommandeClasseHolo(this);
        getCommand("classeholo").setExecutor(commandeClasseHolo);
        getServer().getPluginManager().registerEvents(commandeClasseHolo, this);

        World mondePrincipal = Bukkit.getWorlds().get(0);
        mondePrincipal.setSpawnLocation(LobbyManager.getSpawnPrincipal());
    }
}
