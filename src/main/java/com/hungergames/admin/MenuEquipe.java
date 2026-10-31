package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.equipe.PropositionEquipe;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MenuEquipe implements Listener {
    private static final String TITRE_LISTE = Messages.get("menuequipe.choisir_un_coequipier");
    private static final String TITRE_ATTENTE = Messages.get("menuequipe.en_attente_de_reponse");
    private static final String TITRE_DEMANDE = Messages.get("menuequipe.demande_equipe");
    private static final String TITRE_NEGOCIATION = Messages.get("menuequipe.configuration_equipe");

    private static final Map<ChatColor, Material> COULEURS = new LinkedHashMap<>();

    static {
        COULEURS.put(ChatColor.WHITE, Material.WHITE_WOOL);
        COULEURS.put(ChatColor.GRAY, Material.LIGHT_GRAY_WOOL);
        COULEURS.put(ChatColor.DARK_GRAY, Material.GRAY_WOOL);
        COULEURS.put(ChatColor.BLACK, Material.BLACK_WOOL);
        COULEURS.put(ChatColor.RED, Material.RED_WOOL);
        COULEURS.put(ChatColor.DARK_RED, Material.RED_WOOL);
        COULEURS.put(ChatColor.GOLD, Material.ORANGE_WOOL);
        COULEURS.put(ChatColor.YELLOW, Material.YELLOW_WOOL);
        COULEURS.put(ChatColor.DARK_GREEN, Material.GREEN_WOOL);
        COULEURS.put(ChatColor.GREEN, Material.LIME_WOOL);
        COULEURS.put(ChatColor.DARK_AQUA, Material.CYAN_WOOL);
        COULEURS.put(ChatColor.AQUA, Material.LIGHT_BLUE_WOOL);
        COULEURS.put(ChatColor.DARK_BLUE, Material.BLUE_WOOL);
        COULEURS.put(ChatColor.BLUE, Material.BLUE_WOOL);
        COULEURS.put(ChatColor.DARK_PURPLE, Material.PURPLE_WOOL);
        COULEURS.put(ChatColor.LIGHT_PURPLE, Material.MAGENTA_WOOL);
    }

    private final JavaPlugin plugin;
    private final EquipeManager equipeManager;
    private final RoleManager roleManager;

    private final Set<UUID> joueursDansMenuListe = new HashSet<>();

    private final Set<UUID> menuListeForce = new HashSet<>();

    public MenuEquipe(JavaPlugin plugin, EquipeManager equipeManager, RoleManager roleManager) {
        this.plugin = plugin;
        this.equipeManager = equipeManager;
        this.roleManager = roleManager;
    }

    public static Set<ChatColor> couleursDisponibles() {
        return COULEURS.keySet();
    }

    public void ouvrirMenuBroadcast() {
        ouvrirMenuBroadcast(false);
    }

    public void ouvrirMenuBroadcast(boolean force) {
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            if (equipeManager.aUneEquipe(joueur)) continue;
            if (equipeManager.getDemandeurEnAttente(joueur) != null) continue;
            if (equipeManager.aUneDemandeEnvoyee(joueur)) continue;
            if (equipeManager.aUneNegociationEnCours(joueur)) continue;

            if (force) {
                menuListeForce.add(joueur.getUniqueId());
            }
            ouvrirMenuListe(joueur);
        }
    }

    public void ouvrirMenuPourJoueur(Player player) {
        if (roleManager.estExclu(player)) {
            player.sendMessage(Messages.get("menuequipe.les_admins_afk_ne_participent_pas"));
            return;
        }

        if (equipeManager.aUneEquipe(player)) {
            player.sendMessage(Messages.get("menuequipe.tu_as_deja_une_equipe"));
            return;
        }

        if (equipeManager.aUneNegociationEnCours(player)) {
            ouvrirMenuNegociation(player, equipeManager.getProposition(player));
            return;
        }

        UUID demandeur = equipeManager.getDemandeurEnAttente(player);
        if (demandeur != null) {
            ouvrirMenuDemande(player, demandeur);
            return;
        }

        if (equipeManager.aUneDemandeEnvoyee(player)) {
            ouvrirMenuAttente(player);
            return;
        }

        ouvrirMenuListe(player);
    }

    private static final int SLOT_CREER_SOLO = 8;

    private void ouvrirMenuListe(Player player) {
        player.openInventory(construireMenuListe(player));
        joueursDansMenuListe.add(player.getUniqueId());
    }

    private void rafraichirMenuListe(Player player) {
        if (!joueursDansMenuListe.contains(player.getUniqueId())) {
            return;
        }

        InventoryView vue = player.getOpenInventory();
        if (!TITRE_LISTE.equals(vue.getTitle())) {
            return;
        }

        Inventory contenu = construireMenuListe(player);
        Inventory top = vue.getTopInventory();
        for (int i = 0; i < top.getSize(); i++) {
            top.setItem(i, i < contenu.getSize() ? contenu.getItem(i) : null);
        }
    }

    private void rafraichirTousLesMenusListe() {
        for (UUID uuid : new HashSet<>(joueursDansMenuListe)) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) {
                rafraichirMenuListe(joueur);
            }
        }
    }

    @EventHandler
    public void onJoueurRejoint(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::rafraichirTousLesMenusListe, 1L);
    }

    @EventHandler
    public void onFermetureMenu(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::rafraichirTousLesMenusListe, 1L);
    }

    private boolean aDesAlternatives(Player player) {
        if (!equipeManager.getNomsEquipesSolo().isEmpty()) {
            return true;
        }

        for (Player autre : Bukkit.getOnlinePlayers()) {
            if (autre.equals(player)) continue;
            if (roleManager.estExclu(autre)) continue;
            if (!equipeManager.estDisponiblePourEquipe(autre.getUniqueId())) continue;

            return true;
        }

        return false;
    }

    private Inventory construireMenuListe(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, TITRE_LISTE);

        int slotEquipe = 0;
        for (String nomEquipe : equipeManager.getNomsEquipesSolo()) {
            if (slotEquipe >= SLOT_CREER_SOLO) break;

            ChatColor couleur = equipeManager.getCouleurEquipeSolo(nomEquipe);
            Material material = couleur != null ? COULEURS.getOrDefault(couleur, Material.WHITE_WOOL) : Material.WHITE_WOOL;
            String prefixe = couleur != null ? couleur.toString() : "";

            menu.setItem(slotEquipe, Menus.creerItem(material, prefixe + nomEquipe,
                    List.of(Messages.get("menuequipe.equipe_existante"), Messages.get("menuequipe.clique_pour_la_rejoindre_directement"))));
            slotEquipe++;
        }

        int slot = 9;

        for (Player autre : Bukkit.getOnlinePlayers()) {
            if (autre.equals(player)) continue;
            if (roleManager.estExclu(autre)) continue;

            if (!equipeManager.estDisponiblePourEquipe(autre.getUniqueId())) continue;

            if (MenuClasse.aMenuClasseOuvert(autre)) continue;

            if (slot >= menu.getSize()) break;

            menu.setItem(slot, creerTeteJoueur(autre, List.of(Messages.get("menuequipe.clique_pour_proposer_une_equipe"))));
            slot++;
        }

        if (!aDesAlternatives(player)) {
            menu.setItem(SLOT_CREER_SOLO, Menus.creerItem(Material.EMERALD, Messages.get("menuequipe.jouer_en_solo"),
                    List.of(Messages.get("menuequipe.cree_directement_ta_propre_equipe"), Messages.get("menuequipe.sans_attendre_un_partenaire"))));
        } else if (slot == 9 && slotEquipe == 0) {
            menu.setItem(SLOT_CREER_SOLO, Menus.creerItem(Material.BARRIER, Messages.get("menuequipe.en_attente_un_joueur"),
                    List.of(Messages.get("menuequipe.quelqu_un_est_en_train_de"),
                            Messages.get("menuequipe.sa_tete_reapparaitra_ici_des_qu"))));
        }

        return menu;
    }

    private void ouvrirMenuAttente(Player player) {
        UUID cibleUuid = equipeManager.getCibleDemande(player);
        Player cible = cibleUuid != null ? Bukkit.getPlayer(cibleUuid) : null;

        Inventory menu = Bukkit.createInventory(null, 9, TITRE_ATTENTE);

        if (cible != null) {
            menu.setItem(4, creerTeteJoueur(cible, List.of(Messages.get("menuequipe.en_attente_de_sa_reponse"))));
        }

        menu.setItem(8, Menus.creerItem(Material.BARRIER, Messages.get("menuequipe.annuler_la_demande"), null));

        player.openInventory(menu);
    }

    private void ouvrirMenuDemande(Player player, UUID demandeurUuid) {
        Player demandeur = Bukkit.getPlayer(demandeurUuid);

        Inventory menu = Bukkit.createInventory(null, 9, TITRE_DEMANDE);

        if (demandeur != null) {
            menu.setItem(3, creerTeteJoueur(demandeur, List.of(Messages.get("menuequipe.veut_faire_equipe_avec_toi"))));
        }

        menu.setItem(4, Menus.creerItem(Material.LIME_WOOL, Messages.get("menuequipe.accepter"), null));
        menu.setItem(5, Menus.creerItem(Material.RED_WOOL, Messages.get("menuequipe.refuser"), null));

        player.openInventory(menu);
    }

    private void ouvrirMenuNegociation(Player player, PropositionEquipe proposition) {
        if (proposition == null) {
            return;
        }

        Inventory menu = construireNegociation(proposition, player);
        player.openInventory(menu);
    }

    private static final int SLOT_COULEUR_DEBUT = 9;
    private static final int SLOT_COULEUR_FIN = 24;
    private static final int SLOT_SEPARATION_DEBUT = 27;
    private static final int SLOT_SEPARATION_FIN = 35;
    private static final int SLOT_TETE_1 = 30;
    private static final int SLOT_TETE_2 = 32;
    private static final int SLOT_ANNULER = 36;
    private static final int SLOT_PRET_1 = 39;
    private static final int SLOT_PRET_2 = 41;

    private Inventory construireNegociation(PropositionEquipe proposition, Player observateur) {
        Inventory menu = Bukkit.createInventory(null, 45, TITRE_NEGOCIATION);

        int slot = SLOT_COULEUR_DEBUT;
        for (Map.Entry<ChatColor, Material> entree : COULEURS.entrySet()) {
            if (slot > SLOT_COULEUR_FIN) break;

            boolean selectionnee = entree.getKey() == proposition.getCouleur();
            ItemStack item = Menus.creerItem(entree.getValue(), Messages.get("menuequipe.couleur", "entree", entree.getKey(), "selectionnee", (selectionnee ? Messages.get("menuequipe.texte") : "")), null);

            if (selectionnee) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    meta.setEnchantmentGlintOverride(true);
                    item.setItemMeta(meta);
                }
            }

            menu.setItem(slot, item);
            slot++;
        }

        for (int i = SLOT_SEPARATION_DEBUT; i <= SLOT_SEPARATION_FIN; i++) {
            if (i == SLOT_TETE_1 || i == SLOT_TETE_2) continue;
            menu.setItem(i, Menus.creerItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        menu.setItem(SLOT_ANNULER, Menus.creerItem(Material.BARRIER, Messages.get("commun.annuler"),
                List.of(Messages.get("menuequipe.abandonne_cette_equipe_et"), Messages.get("menuequipe.retourne_au_choix_de_coequipier"))));

        Player joueur1 = Bukkit.getPlayer(proposition.getJoueur1());
        Player joueur2 = Bukkit.getPlayer(proposition.getJoueur2());

        String nom1 = joueur1 != null ? joueur1.getName() : "?";
        String nom2 = joueur2 != null ? joueur2.getName() : "?";

        menu.setItem(SLOT_TETE_1, creerTeteProprietaire(joueur1, nom1, observateur, proposition, proposition.getJoueur1()));
        menu.setItem(SLOT_TETE_2, creerTeteProprietaire(joueur2, nom2, observateur, proposition, proposition.getJoueur2()));

        menu.setItem(SLOT_PRET_1, creerBoutonPret(proposition, proposition.getJoueur1(), nom1, observateur));
        menu.setItem(SLOT_PRET_2, creerBoutonPret(proposition, proposition.getJoueur2(), nom2, observateur));

        return menu;
    }

    private ItemStack creerTeteProprietaire(Player proprietaire, String pseudo, Player observateur,
                                            PropositionEquipe proposition, UUID uuid) {
        boolean cestMoi = observateur != null && observateur.getUniqueId().equals(uuid);
        boolean pret = proposition.estPret(uuid);

        ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta itemMeta = tete.getItemMeta();

        if (itemMeta instanceof SkullMeta meta) {
            if (proprietaire != null) {
                meta.setOwningPlayer(proprietaire);
            }
            meta.setDisplayName((cestMoi ? Messages.get("menuequipe.toi") : Messages.get("menuequipe.texte_2")) + pseudo);
            meta.setLore(List.of(
                    cestMoi ? Messages.get("menuequipe.ton_bouton_est_juste_en_dessous") : Messages.get("menuequipe.son_bouton_est_juste_en_dessous"),
                    pret ? Messages.get("menuequipe.pret") : Messages.get("menuequipe.pas_encore_pret")
            ));
            tete.setItemMeta(meta);
        }

        return tete;
    }

    private ItemStack creerBoutonPret(PropositionEquipe proposition, UUID uuid, String pseudo, Player observateur) {
        boolean pret = proposition.estPret(uuid);
        boolean cestMoi = observateur != null && observateur.getUniqueId().equals(uuid);

        return Menus.creerItem(
                pret ? Material.LIME_DYE : Material.GRAY_DYE,
                Messages.get("menuequipe.nom_pseudo_cestmoi", "nom", proposition.getNom(), "pseudo", pseudo, "cestmoi", (cestMoi ? Messages.get("menuequipe.toi_2") : "")),
                List.of(
                        pret ? Messages.get("menuequipe.pret") : Messages.get("menuequipe.pas_encore_pret"),
                        "",
                        cestMoi ? Messages.get("menuequipe.clique_pour_changer_ton_etat") : Messages.get("menuequipe.bouton_de_pas_cliquable", "pseudo", pseudo)
                )
        );
    }

    private void rafraichirNegociation(PropositionEquipe proposition) {
        for (UUID uuid : List.of(proposition.getJoueur1(), proposition.getJoueur2())) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur == null) continue;

            InventoryView vue = joueur.getOpenInventory();
            if (TITRE_NEGOCIATION.equals(vue.getTitle())) {
                Inventory contenu = construireNegociation(proposition, joueur);
                for (int i = 0; i < contenu.getSize(); i++) {
                    vue.getTopInventory().setItem(i, contenu.getItem(i));
                }
            }
        }
    }

    private ItemStack creerTeteJoueur(Player cible, List<String> lore) {
        ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta itemMeta = tete.getItemMeta();

        if (itemMeta instanceof SkullMeta meta) {
            meta.setOwningPlayer(cible);
            meta.setDisplayName(Messages.get("commun.cible", "cible", cible.getName()));
            meta.setLore(lore);
            tete.setItemMeta(meta);
        }

        return tete;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titre = event.getView().getTitle();

        boolean estListe = titre.equals(TITRE_LISTE);
        boolean estAttente = titre.equals(TITRE_ATTENTE);
        boolean estDemande = titre.equals(TITRE_DEMANDE);
        boolean estNegociation = titre.equals(TITRE_NEGOCIATION);

        if (!estListe && !estAttente && !estDemande && !estNegociation) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }

        if (estListe) {
            gererClicListe(player, item);
        } else if (estAttente) {
            gererClicAttente(player, item);
        } else if (estDemande) {
            gererClicDemande(player, item);
        } else {
            gererClicNegociation(player, rawSlot, item);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!TITRE_LISTE.equals(event.getView().getTitle())) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (equipeManager.aUneEquipe(player)) {
            menuListeForce.remove(uuid);
        }

        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) {
            return;
        }

        joueursDansMenuListe.remove(uuid);

        if (!menuListeForce.contains(uuid)) {
            return;
        }

        if (equipeManager.aUneNegociationEnCours(player) || equipeManager.aUneDemandeEnvoyee(player)
                || equipeManager.getDemandeurEnAttente(player) != null) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !menuListeForce.contains(uuid)) return;
            if (equipeManager.aUneEquipe(player)) return;
            if (equipeManager.aUneNegociationEnCours(player) || equipeManager.aUneDemandeEnvoyee(player)
                    || equipeManager.getDemandeurEnAttente(player) != null) return;

            ouvrirMenuListe(player);
        }, 1L);
    }

    private void gererClicListe(Player player, ItemStack item) {
        if (roleManager.estExclu(player)) {
            player.sendMessage(Messages.get("menuequipe.impossible_tu_as_le_role_admin"));
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        if (!equipeManager.estDisponiblePourEquipe(player.getUniqueId())) {
            player.sendMessage(Messages.get("menuequipe.impossible_tu_es_deja_engage_dans"));
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        if (item.getType() == Material.EMERALD) {
            if (aDesAlternatives(player)) {
                player.sendMessage(Messages.get("menuequipe.un_partenaire_est_disponible_choisis_le"));
                Bukkit.getScheduler().runTask(plugin, () -> ouvrirMenuListe(player));
                return;
            }

            equipeManager.formerEquipeSolo(player);
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            rafraichirTousLesMenusListe();
            return;
        }

        if (item.getType() == Material.BARRIER) {
            return;
        }

        if (item.getType() != Material.PLAYER_HEAD) {
            gererClicRejoindreEquipe(player, item);
            return;
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof SkullMeta meta) || meta.getOwningPlayer() == null) {
            return;
        }

        Player cible = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (cible == null) {
            player.sendMessage(Messages.get("menuequipe.ce_joueur_est_plus_en_ligne"));
            return;
        }

        if (equipeManager.estEnCooldown(player)) {
            long restant = equipeManager.getCooldownRestant(player) / 1000 + 1;
            player.sendMessage(Messages.get("menuequipe.tu_dois_attendre_encore_avant_de", "restant", restant));
            return;
        }

        boolean envoye = equipeManager.envoyerDemande(player, cible);

        if (!envoye) {
            player.sendMessage(Messages.get("menuequipe.impossible_envoyer_la_demande_deja_occupe"));
            return;
        }

        player.sendMessage(Messages.get("menuequipe.demande_envoyee", "cible", cible.getName()));

        Bukkit.getScheduler().runTask(plugin, () -> {
            ouvrirMenuAttente(player);
            ouvrirMenuDemande(cible, player.getUniqueId());
        });

        rafraichirTousLesMenusListe();
    }

    private void gererClicRejoindreEquipe(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        String nomEquipe = ChatColor.stripColor(meta.getDisplayName());
        EquipeManager.ResultatAjout resultat = equipeManager.ajouterAEquipeExistante(player, nomEquipe);

        switch (resultat) {
            case SUCCES -> {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                rafraichirTousLesMenusListe();
            }
            case JOUEUR_DEJA_EN_EQUIPE -> player.sendMessage(Messages.get("menuequipe.tu_as_deja_une_equipe"));
            case EQUIPE_INTROUVABLE -> {
                player.sendMessage(Messages.get("menuequipe.cette_equipe_existe_plus"));
                Bukkit.getScheduler().runTask(plugin, () -> ouvrirMenuListe(player));
            }
            case EQUIPE_DEJA_COMPLETE -> player.sendMessage(Messages.get("menuequipe.cette_equipe_est_deja_complete"));
        }
    }

    private void gererClicAttente(Player player, ItemStack item) {
        if (item.getType() != Material.BARRIER) {
            return;
        }

        equipeManager.annulerDemande(player);
        player.sendMessage(Messages.get("menuequipe.demande_annulee"));

        Bukkit.getScheduler().runTask(plugin, () -> ouvrirMenuListe(player));
        rafraichirTousLesMenusListe();
    }

    private void gererClicDemande(Player player, ItemStack item) {
        UUID demandeurUuid = equipeManager.getDemandeurEnAttente(player);

        if (item.getType() == Material.LIME_WOOL) {
            PropositionEquipe proposition = equipeManager.accepterDemande(player);

            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());

            if (proposition != null) {
                Player demandeur = Bukkit.getPlayer(demandeurUuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ouvrirMenuNegociation(player, proposition);
                    if (demandeur != null) {
                        ouvrirMenuNegociation(demandeur, proposition);
                    }
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> ouvrirMenuListe(player));
            }

            rafraichirTousLesMenusListe();
        } else if (item.getType() == Material.RED_WOOL) {
            equipeManager.refuserDemande(player);

            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());

            if (demandeurUuid != null) {
                Player demandeur = Bukkit.getPlayer(demandeurUuid);
                if (demandeur != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> demandeur.closeInventory());
                }
            }

            rafraichirTousLesMenusListe();
        }
    }

    private void gererClicNegociation(Player player, int slot, ItemStack item) {
        PropositionEquipe proposition = equipeManager.getProposition(player);
        if (proposition == null) {
            return;
        }

        if (slot == SLOT_ANNULER) {
            equipeManager.annulerProposition(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            rafraichirTousLesMenusListe();
            return;
        }

        if (slot == SLOT_PRET_1 || slot == SLOT_PRET_2) {
            boolean estSonSlot = (slot == SLOT_PRET_1 && player.getUniqueId().equals(proposition.getJoueur1()))
                    || (slot == SLOT_PRET_2 && player.getUniqueId().equals(proposition.getJoueur2()));

            if (!estSonSlot) {
                return;
            }

            proposition.setPret(player.getUniqueId(), !proposition.estPret(player.getUniqueId()));

            if (proposition.estFinalisable()) {
                equipeManager.finaliserEquipe(proposition);

                for (UUID uuid : List.of(proposition.getJoueur1(), proposition.getJoueur2())) {
                    Player joueur = Bukkit.getPlayer(uuid);
                    if (joueur != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> joueur.closeInventory());
                    }
                }
                rafraichirTousLesMenusListe();
                return;
            }

            rafraichirNegociation(proposition);
            return;
        }

        if (slot >= SLOT_COULEUR_DEBUT && slot <= SLOT_COULEUR_FIN) {
            int index = 0;
            for (ChatColor couleur : COULEURS.keySet()) {
                if (index == slot - SLOT_COULEUR_DEBUT) {
                    proposition.setCouleur(couleur);
                    rafraichirNegociation(proposition);
                    break;
                }
                index++;
            }
        }
    }
}
