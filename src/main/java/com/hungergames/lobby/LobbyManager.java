package com.hungergames.lobby;

import com.hungergames.admin.AdminKitManager;
import com.hungergames.admin.MatchAdminKit;
import com.hungergames.admin.MenuClasse;
import com.hungergames.admin.MenuEquipe;
import com.hungergames.classe.Classe;
import com.hungergames.classe.ClasseManager;
import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.game.FreezeManager;
import com.hungergames.game.KillTracker;
import com.hungergames.hud.Titre;
import com.hungergames.map.MapManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class LobbyManager implements Listener {
    public enum EtatPartie { OFF, LOBBY, FERMETURE }

    private static final String TITRE_MODIFIER = Messages.get("lobbymanager.modifier");

    private static final long DELAI_RATTRAPAGE_FERMETURE = 10L;

    private final JavaPlugin plugin;
    private final ClasseManager classeManager;
    private final EquipeManager equipeManager;
    private final RoleManager roleManager;
    private final MenuClasse menuClasse;
    private final MenuEquipe menuEquipe;
    private final MapManager mapManager;
    private final KillTracker killTracker;
    private final AdminKitManager adminKitManager;
    private final FreezeManager freezeManager;

    private MatchAdminKit matchAdminKit;

    private final Random random = new Random();

    private EtatPartie etat = EtatPartie.OFF;
    private BukkitTask tacheSurveillanceFermeture;

    private final BarresLobby barres = new BarresLobby();

    private final Set<UUID> joueursAResynchroniser = new HashSet<>();

    public LobbyManager(JavaPlugin plugin, ClasseManager classeManager, EquipeManager equipeManager, RoleManager roleManager, MenuClasse menuClasse, MenuEquipe menuEquipe, MapManager mapManager, KillTracker killTracker, AdminKitManager adminKitManager, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.classeManager = classeManager;
        this.equipeManager = equipeManager;
        this.roleManager = roleManager;
        this.menuClasse = menuClasse;
        this.menuEquipe = menuEquipe;
        this.mapManager = mapManager;
        this.killTracker = killTracker;
        this.adminKitManager = adminKitManager;
        this.freezeManager = freezeManager;
    }

    private void retirerClasseEtEquipe(Player joueur) {
        if (classeManager.getClasse(joueur).isPresent()) {
            classeManager.retirerClasse(joueur);
        }
        if (equipeManager.aUneEquipe(joueur)) {
            equipeManager.retirerEquipe(joueur);
        }
    }

    private void reinitialiserVie(Player joueur) {
        joueur.setMaxHealth(20.0);
        joueur.setHealth(20.0);
    }

    private void nettoyerEffets(Player joueur) {
        for (PotionEffect effet : joueur.getActivePotionEffects()) {
            joueur.removePotionEffect(effet.getType());
        }
    }

    private void rendreTrousseSiAdmin(Player joueur) {
        if (roleManager.estAdmin(joueur)) {
            adminKitManager.donnerKit(joueur);
        }
    }

    public void setMatchAdminKit(MatchAdminKit matchAdminKit) {
        this.matchAdminKit = matchAdminKit;
    }

    public EtatPartie getEtat() {
        return etat;
    }

    public boolean isLobbyActif() {
        return etat == EtatPartie.LOBBY;
    }

    public boolean isFermetureActive() {
        return etat == EtatPartie.FERMETURE;
    }

    public void desactiver() {
        etat = EtatPartie.OFF;
        annulerSurveillanceFermeture();
        mapManager.retourLobby();
        equipeManager.reinitialiserCompteurDistrict();

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            ObjetsLobby.retirerObjetsChoix(joueur);
            barres.nettoyerToutesBarres(joueur);

            equipeManager.nettoyer(joueur);
            retirerClasseEtEquipe(joueur);

            if (roleManager.estAdmin(joueur)) {
                roleManager.retirerAdmin(joueur);
            }
            if (roleManager.estAfk(joueur)) {
                roleManager.retirerAfk(joueur);
            }

            reinitialiserVie(joueur);
            nettoyerEffets(joueur);
            appliquerGamemodeAventure(joueur);

            joueur.getInventory().clear();
        }
    }

    public void resynchroniserJoueurOff(Player joueur) {
        joueursAResynchroniser.remove(joueur.getUniqueId());

        ObjetsLobby.retirerObjetsChoix(joueur);
        barres.nettoyerToutesBarres(joueur);

        equipeManager.nettoyer(joueur);
        retirerClasseEtEquipe(joueur);

        reinitialiserVie(joueur);
        nettoyerEffets(joueur);
        appliquerGamemodeAventure(joueur);
        mapManager.teleporterVersLobby(joueur);

        if (roleManager.estAdmin(joueur)) {
            adminKitManager.donnerKit(joueur);
        } else {
            joueur.getInventory().clear();
        }

        joueur.sendMessage(Messages.get("lobbymanager.tu_as_ete_repasse_en_mode"));
    }

    public void resynchroniserJoueurLobby(Player joueur) {
        joueursAResynchroniser.remove(joueur.getUniqueId());

        barres.nettoyerToutesBarres(joueur);

        equipeManager.nettoyer(joueur);
        retirerClasseEtEquipe(joueur);

        reinitialiserVie(joueur);
        nettoyerEffets(joueur);
        appliquerGamemodeAventure(joueur);
        appliquerSaturationLobby(joueur);
        mapManager.teleporterVersLobby(joueur);

        if (roleManager.estAdmin(joueur)) {
            adminKitManager.donnerKit(joueur);
        } else {
            joueur.getInventory().clear();

            if (!roleManager.estExclu(joueur)) {
                donnerObjetsManquants(joueur);
                afficherBarresInitiales(joueur);
            }
        }

        joueur.sendMessage(Messages.get("lobbymanager.tu_as_ete_resynchronise_sur_le"));
    }

    public void activerLobby() {
        etat = EtatPartie.LOBBY;
        annulerSurveillanceFermeture();

        Bukkit.broadcastMessage(Messages.get("lobbymanager.lobby_active"));
        mapManager.retourLobby();

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            appliquerGamemodeAventure(joueur);
            nettoyerEffets(joueur);
            appliquerSaturationLobby(joueur);
            reinitialiserVie(joueur);

            if (roleManager.estAdmin(joueur)) {
                adminKitManager.donnerKit(joueur);
                continue;
            }

            joueur.getInventory().clear();

            if (roleManager.estExclu(joueur)) continue;
            donnerObjetsManquants(joueur);
            afficherBarresInitiales(joueur);
        }
    }

    private void appliquerGamemodeAventure(Player joueur) {
        if (roleManager.estAdmin(joueur)) {
            return;
        }
        joueur.setGameMode(GameMode.ADVENTURE);
    }

    private void appliquerSaturationLobby(Player joueur) {
        joueur.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false, false));
    }

    public void activerFermeture() {
        etat = EtatPartie.FERMETURE;
        annulerSurveillanceFermeture();

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            ObjetsLobby.retirerObjetModifier(joueur);
            reinitialiserVie(joueur);
            nettoyerEffets(joueur);
            appliquerSaturationLobby(joueur);
            if (roleManager.estAdmin(joueur)) {
                adminKitManager.donnerKit(joueur);
            }
        }

        Bukkit.broadcastMessage(Messages.get("lobbymanager.fermeture_du_lobby"));
        Bukkit.broadcastMessage(Messages.get("lobbymanager.partie_en_attente_verification_des_choix"));

        menuEquipe.ouvrirMenuBroadcast();
        envoyerRecapitulatifAuxAdmins();

        Bukkit.getScheduler().runTaskLater(plugin, this::rattraperChoixManquants, DELAI_RATTRAPAGE_FERMETURE);

        tacheSurveillanceFermeture = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (etat != EtatPartie.FERMETURE) {
                annulerSurveillanceFermeture();
                return;
            }

            if (!toutLeMondeEstPret()) {
                afficherRecapitulatifActionbar();
                return;
            }

            annoncerFermetureTerminee();
            annulerSurveillanceFermeture();
        }, 20L, 20L);
    }

    private void envoyerRecapitulatifAuxAdmins() {
        List<String> sansClasse = joueursSansClasse();
        List<String> sansEquipe = joueursSansEquipe();

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!roleManager.estAdmin(admin)) continue;

            admin.sendMessage(sansClasse.isEmpty()
                    ? Messages.get("commun.tout_le_monde_une_classe")
                    : Messages.get("commun.sans_classe", "sansclasse", String.join(Messages.get("commun.separateur_liste"), sansClasse)));
            admin.sendMessage(sansEquipe.isEmpty()
                    ? Messages.get("commun.tout_le_monde_une_equipe")
                    : Messages.get("commun.sans_equipe", "sansequipe", String.join(Messages.get("commun.separateur_liste"), sansEquipe)));
        }
    }

    private void afficherRecapitulatifActionbar() {
        int sansClasse = joueursSansClasse().size();
        int sansEquipe = joueursSansEquipe().size();

        if (sansClasse == 0 && sansEquipe == 0) {
            return;
        }

        String message = Messages.get("lobbymanager.en_attente_sans_classe_sans_equipe", "sansclasse", sansClasse, "sansequipe", sansEquipe);
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (roleManager.estAdmin(admin)) {
                admin.sendActionBar(message);
            }
        }
    }

    private void rattraperChoixManquants() {
        if (etat != EtatPartie.FERMETURE) {
            return;
        }

        List<Player> sansEquipe = new ArrayList<>();
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            if (equipeManager.aUneNegociationEnCours(joueur)) continue;
            if (equipeManager.aUneDemandeEnvoyee(joueur)) continue;
            if (equipeManager.getDemandeurEnAttente(joueur) != null) continue;
            if (!equipeManager.aUneEquipe(joueur)) {
                sansEquipe.add(joueur);
            }
        }
        Collections.shuffle(sansEquipe, random);

        for (int i = 0; i + 1 < sansEquipe.size(); i += 2) {
            equipeManager.formerEquipeAleatoire(sansEquipe.get(i), sansEquipe.get(i + 1));
        }
        if (sansEquipe.size() % 2 == 1) {
            equipeManager.formerEquipeSolo(sansEquipe.get(sansEquipe.size() - 1));
        }

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            if (equipeManager.aUneNegociationEnCours(joueur)) continue;
            if (equipeManager.aUneDemandeEnvoyee(joueur)) continue;
            if (equipeManager.getDemandeurEnAttente(joueur) != null) continue;
            if (classeManager.getClasse(joueur).isEmpty()) {
                menuClasse.ouvrirMenuPourJoueur(joueur);
            }
        }

        envoyerRecapitulatifAuxAdmins();
    }

    public boolean toutLeMondeEstPret() {
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            if (classeManager.getClasse(joueur).isEmpty()) return false;
            if (!equipeManager.aUneEquipe(joueur)) return false;
        }
        return true;
    }

    public List<String> joueursIncomplets() {
        List<String> manquants = new ArrayList<>();
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;

            boolean aClasse = classeManager.getClasse(joueur).isPresent();
            boolean aEquipe = equipeManager.aUneEquipe(joueur);

            if (!aClasse || !aEquipe) {
                manquants.add(joueur.getName());
            }
        }
        return manquants;
    }

    public List<String> joueursSansClasse() {
        List<String> noms = new ArrayList<>();
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            if (classeManager.getClasse(joueur).isEmpty()) {
                noms.add(joueur.getName());
            }
        }
        return noms;
    }

    public List<String> joueursSansEquipe() {
        List<String> noms = new ArrayList<>();
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            if (!equipeManager.aUneEquipe(joueur)) {
                noms.add(joueur.getName());
            }
        }
        return noms;
    }

    private void annoncerFermetureTerminee() {
        int[] parClasse = new int[Classe.values().length];
        int nombreEquipes = 0;
        Set<Player> dejaComptes = new HashSet<>();
        int totalJoueurs = 0;

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            totalJoueurs++;

            classeManager.getClasse(joueur).ifPresent(c -> parClasse[c.ordinal()]++);

            if (dejaComptes.contains(joueur)) continue;
            dejaComptes.add(joueur);

            UUID partenaireUuid = equipeManager.getPartenaire(joueur);
            Player partenaire = partenaireUuid != null ? Bukkit.getPlayer(partenaireUuid) : null;
            if (partenaire != null) {
                dejaComptes.add(partenaire);
            }
            nombreEquipes++;
        }

        Bukkit.broadcastMessage(Messages.get("lobbymanager.tout_le_monde_est_pret_une"));
        Bukkit.broadcastMessage(Messages.get("lobbymanager.joueurs", "totaljoueurs", totalJoueurs));

        for (Classe classe : Classe.values()) {
            Bukkit.broadcastMessage(Messages.get("lobbymanager.classe_parclasse", "classe", classe.getDisplayName(), "parclasse", parClasse[classe.ordinal()]));
        }

        Bukkit.broadcastMessage(Messages.get("lobbymanager.equipes_formees", "nombreequipes", nombreEquipes));
        Bukkit.broadcastMessage(Messages.get("lobbymanager.les_admins_peuvent_maintenant_lancer_go"));

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            ObjetsLobby.retirerObjetsChoix(joueur);

            ObjetsLobby.retirerObjetModifier(joueur);
            barres.nettoyerToutesBarres(joueur);
            joueur.closeInventory();
        }
    }

    private void annulerSurveillanceFermeture() {
        if (tacheSurveillanceFermeture != null) {
            tacheSurveillanceFermeture.cancel();
            tacheSurveillanceFermeture = null;
        }
    }

    public void donnerObjetsManquants(Player player) {
        if (classeManager.getClasse(player).isEmpty() && !ObjetsLobby.possedeObjetClasse(player)) {
            player.getInventory().addItem(ObjetsLobby.creerItemClasse());
        }
        if (!equipeManager.aUneEquipe(player) && !ObjetsLobby.possedeObjetEquipe(player)) {
            player.getInventory().addItem(ObjetsLobby.creerItemEquipe());
        }
    }

    public void surEquipeFormee(Player joueur1, Player joueur2) {
        ObjetsLobby.retirerObjetEquipe(joueur1);
        ObjetsLobby.retirerObjetEquipe(joueur2);
        barres.retirerBarreEquipe(joueur1);
        barres.retirerBarreEquipe(joueur2);
        verifierBarreAttente(joueur1);
        verifierBarreAttente(joueur2);

        if (classeManager.getClasse(joueur1).isEmpty() && !roleManager.estExclu(joueur1)) {
            menuClasse.ouvrirMenuPourJoueur(joueur1);
        }
        if (classeManager.getClasse(joueur2).isEmpty() && !roleManager.estExclu(joueur2)) {
            menuClasse.ouvrirMenuPourJoueur(joueur2);
        }
    }

    public void surClasseAssignee(Player joueur) {
        ObjetsLobby.retirerObjetClasse(joueur);
        barres.retirerBarreClasse(joueur);
        verifierBarreAttente(joueur);

        if (etat == EtatPartie.LOBBY) {
            ObjetsLobby.donnerObjetModifier(joueur);
        }
    }

    private void ouvrirMenuModifier(Player player) {
        Inventory menu = Bukkit.createInventory(null, 9, TITRE_MODIFIER);

        ItemStack classeItem = new ItemStack(Material.BOOK);
        ItemMeta classeMeta = classeItem.getItemMeta();
        if (classeMeta != null) {
            classeMeta.setDisplayName(Messages.get("lobbymanager.changer_de_classe"));
            classeMeta.setLore(List.of(Messages.get("lobbymanager.retire_ta_classe_actuelle_et"), Messages.get("lobbymanager.rouvre_le_choix_de_classe")));
            classeItem.setItemMeta(classeMeta);
        }
        menu.setItem(3, classeItem);

        ItemStack equipeItem = new ItemStack(Material.LEAD);
        ItemMeta equipeMeta = equipeItem.getItemMeta();
        if (equipeMeta != null) {
            equipeMeta.setDisplayName(Messages.get("lobbymanager.changer_equipe"));
            equipeMeta.setLore(List.of(Messages.get("lobbymanager.quitte_ton_equipe_actuelle_et"), Messages.get("lobbymanager.rouvre_le_choix_equipe")));
            equipeItem.setItemMeta(equipeMeta);
        }
        menu.setItem(5, equipeItem);

        player.openInventory(menu);
    }

    @EventHandler
    public void onModifierClick(InventoryClickEvent event) {
        if (!TITRE_MODIFIER.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }

        if (item.getType() == Material.BOOK) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                if (classeManager.getClasse(player).isPresent()) {
                    classeManager.retirerClasse(player);
                }
                menuClasse.ouvrirMenuPourJoueur(player);
            });
        } else if (item.getType() == Material.LEAD) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                if (equipeManager.aUneEquipe(player)) {
                    equipeManager.quitterEquipe(player);
                }
                menuEquipe.ouvrirMenuPourJoueur(player);
            });
        }
    }

    public void afficherBarresInitiales(Player player) {
        if (roleManager.estExclu(player)) return;

        boolean aClasse = classeManager.getClasse(player).isPresent();
        boolean aEquipe = equipeManager.aUneEquipe(player);

        if (!aEquipe) {
            barres.creerBarreEquipe(player);
        }
        if (!aClasse) {
            barres.creerBarreClasse(player);
        }
        if (aClasse && aEquipe) {
            barres.creerBarreAttente(player);
        }
    }

    private void verifierBarreAttente(Player player) {
        if (etat != EtatPartie.LOBBY) return;
        if (roleManager.estExclu(player)) return;

        boolean aClasse = classeManager.getClasse(player).isPresent();
        boolean aEquipe = equipeManager.aUneEquipe(player);

        if (aClasse && aEquipe) {
            barres.creerBarreAttente(player);
        }
    }

    public static Location getSpawnPrincipal() {
        World monde = Bukkit.getWorlds().get(0);
        if (Reglages.booleen("lobby.use-world-spawn")) {
            return monde.getSpawnLocation();
        }
        return new Location(monde, Reglages.decimal("lobby.x"), Reglages.decimal("lobby.y"), Reglages.decimal("lobby.z"),
                (float) Reglages.decimal("lobby.yaw"), (float) Reglages.decimal("lobby.pitch"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        player.setInvulnerable(false);

        if (!player.hasPlayedBefore()) {
            player.teleport(getSpawnPrincipal());
        }

        if (etat == EtatPartie.OFF) {
            appliquerGamemodeAventure(player);

            retirerClasseEtEquipe(player);

            if (joueursAResynchroniser.remove(player.getUniqueId())) {
                player.getInventory().clear();
                rendreTrousseSiAdmin(player);
                mapManager.teleporterVersLobby(player);
            }
            return;
        }

        if (etat == EtatPartie.FERMETURE) {
            if (killTracker.estPartieDemarree() || freezeManager.compteAReboursEnCours()) {
                if (roleManager.estAdmin(player)) {
                    player.sendMessage(Messages.get("lobbymanager.partie_en_cours_tu_reprends_ton"));
                    if (matchAdminKit != null) {
                        matchAdminKit.donnerKitPourJoueurPromuEnJeu(player);
                    } else {
                        adminKitManager.donnerKit(player);
                    }
                    return;
                }

                appliquerGamemodeAventure(player);
                Titre.enFile(player, Messages.get("commun.partie_en_cours"), Messages.get("lobbymanager.attends_la_fin_de_la_partie"), 10, 60, 20);
                player.sendMessage(Messages.get("lobbymanager.une_partie_est_en_cours_tu"));
                mapManager.teleporterVersLobby(player);

                killTracker.eliminerCarSortiDuMatch(player);
                return;
            }

            appliquerGamemodeAventure(player);
            Titre.enFile(player, Messages.get("lobbymanager.lobby_ferme"), Messages.get("lobbymanager.attends_la_prochaine_partie"), 10, 50, 20);

            if (joueursAResynchroniser.remove(player.getUniqueId())) {
                retirerClasseEtEquipe(player);
                player.getInventory().clear();
                rendreTrousseSiAdmin(player);
                mapManager.teleporterVersLobby(player);
            }

            completerArriveeTardive(player);
            return;
        }

        if (etat == EtatPartie.LOBBY) {
            appliquerGamemodeAventure(player);
            appliquerSaturationLobby(player);

            if (joueursAResynchroniser.remove(player.getUniqueId())) {
                retirerClasseEtEquipe(player);
                player.getInventory().clear();
                rendreTrousseSiAdmin(player);
                reinitialiserVie(player);
                nettoyerEffets(player);
                mapManager.teleporterVersLobby(player);
            }

            if (!roleManager.estExclu(player)) {
                donnerObjetsManquants(player);
                afficherBarresInitiales(player);

                if (classeManager.getClasse(player).isPresent()) {
                    ObjetsLobby.donnerObjetModifier(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player joueur = event.getPlayer();
        barres.nettoyerToutesBarres(joueur);

        if (killTracker.estPartieDemarree()) {
            joueursAResynchroniser.add(joueur.getUniqueId());
            return;
        }

        if (freezeManager.compteAReboursEnCours()) {
            return;
        }

        if (classeManager.getClasse(joueur).isPresent()) {
            classeManager.retirerClasse(joueur);
        }
        if (equipeManager.aUneEquipe(joueur)) {
            equipeManager.quitterEquipe(joueur);
        }
    }

    private void completerArriveeTardive(Player player) {
        if (roleManager.estExclu(player)) {
            return;
        }

        if (classeManager.getClasse(player).isEmpty()) {
            menuClasse.ouvrirMenuPourJoueur(player);
        }

        if (!equipeManager.aUneEquipe(player)) {
            Player partenaireTrouve = null;

            for (Player autre : Bukkit.getOnlinePlayers()) {
                if (autre.equals(player)) continue;
                if (roleManager.estExclu(autre)) continue;
                if (!equipeManager.estDisponiblePourEquipe(autre.getUniqueId())) continue;

                equipeManager.formerEquipeAleatoire(player, autre);
                partenaireTrouve = autre;
                break;
            }

            if (partenaireTrouve == null) {
                equipeManager.formerEquipeSolo(player);
            }
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack enMain = player.getInventory().getItemInMainHand();

        if (ObjetsLobby.correspond(enMain, Material.LEAD, ObjetsLobby.MODEL_DATA_EQUIPE)) {
            event.setCancelled(true);
            menuEquipe.ouvrirMenuPourJoueur(player);
            return;
        }

        if (ObjetsLobby.correspond(enMain, Material.BOOK, ObjetsLobby.MODEL_DATA_CLASSE)) {
            event.setCancelled(true);
            menuClasse.ouvrirMenuPourJoueur(player);
            return;
        }

        if (ObjetsLobby.correspond(enMain, Material.BARRIER, ObjetsLobby.MODEL_DATA_MODIFIER)) {
            event.setCancelled(true);
            ouvrirMenuModifier(player);
        }
    }

    @EventHandler
    public void onDropObjetChoix(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (ObjetsLobby.correspond(item, Material.LEAD, ObjetsLobby.MODEL_DATA_EQUIPE)
                || ObjetsLobby.correspond(item, Material.BOOK, ObjetsLobby.MODEL_DATA_CLASSE)
                || ObjetsLobby.correspond(item, Material.BARRIER, ObjetsLobby.MODEL_DATA_MODIFIER)) {
            event.setCancelled(true);
        }
    }
}
