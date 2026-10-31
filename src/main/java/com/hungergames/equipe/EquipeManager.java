package com.hungergames.equipe;

import com.hungergames.admin.MenuClasse;
import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.hud.Titre;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class EquipeManager implements Listener {
    private static final String PREFIXE_EQUIPE = "hg_eq_";

    private static final long COOLDOWN_REFUS = Reglages.entier("teams.refusal-cooldown-seconds") * 1000L;

    private final RoleManager roleManager;

    private final Map<UUID, UUID> partenaireDe = new HashMap<>();
    private final Map<UUID, String> nomEquipeDe = new HashMap<>();

    private final Set<UUID> equipesSolo = new HashSet<>();

    private final Map<String, String> teamKeyParNomLower = new HashMap<>();

    private final Map<String, String> nomAfficheParTeamKey = new HashMap<>();

    private final Map<UUID, UUID> demandesEnvoyees = new HashMap<>();
    private final Map<UUID, UUID> demandesRecues = new HashMap<>();
    private final Map<UUID, Long> cooldownRefus = new HashMap<>();

    private final Map<UUID, PropositionEquipe> propositionsEnCours = new HashMap<>();

    private final AtomicInteger compteurEquipe = new AtomicInteger(0);

    private final AtomicInteger compteurDistrict = new AtomicInteger(0);
    private final Random random = new Random();

    private final List<BiConsumer<Player, Player>> ecouteursFormation = new ArrayList<>();

    public EquipeManager(RoleManager roleManager) {
        this.roleManager = roleManager;
        purgerAnciennesEquipes();
    }

    // Scoreboard persistant : après un arrêt brutal, une team restante ferait échouer registerNewTeam.
    private void purgerAnciennesEquipes() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            String nom = team.getName();

            boolean estTeamDuPlugin = nom.startsWith(PREFIXE_EQUIPE)
                    || nom.startsWith("role_")
                    || nom.startsWith("au_prime_");

            if (estTeamDuPlugin) {
                team.unregister();
            }
        }
    }

    public void ajouterEcouteurFormation(BiConsumer<Player, Player> ecouteur) {
        ecouteursFormation.add(ecouteur);
    }

    public boolean aUneEquipe(Player player) {
        return aUneEquipe(player.getUniqueId());
    }

    public boolean aUneEquipe(UUID uuid) {
        return partenaireDe.containsKey(uuid) || equipesSolo.contains(uuid);
    }

    public boolean estDisponiblePourEquipe(UUID uuid) {
        return !aUneEquipe(uuid)
                && !demandesEnvoyees.containsKey(uuid)
                && !demandesRecues.containsKey(uuid)
                && !propositionsEnCours.containsKey(uuid);
    }

    public UUID getPartenaire(Player player) {
        return partenaireDe.get(player.getUniqueId());
    }

    public UUID getPartenaire(UUID uuid) {
        return partenaireDe.get(uuid);
    }

    public List<List<Player>> getEquipesEnLigne() {
        List<List<Player>> equipes = new ArrayList<>();
        Set<UUID> dejaVus = new HashSet<>();

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            UUID uuid = joueur.getUniqueId();
            if (dejaVus.contains(uuid)) continue;

            UUID partenaireUuid = partenaireDe.get(uuid);
            if (partenaireUuid != null) {
                dejaVus.add(uuid);

                Player partenaire = Bukkit.getPlayer(partenaireUuid);
                if (partenaire != null) {
                    dejaVus.add(partenaireUuid);
                    equipes.add(List.of(joueur, partenaire));
                } else {
                    equipes.add(List.of(joueur));
                }
                continue;
            }

            if (equipesSolo.contains(uuid)) {
                dejaVus.add(uuid);
                equipes.add(List.of(joueur));
            }
        }

        return equipes;
    }

    public boolean aUneDemandeEnvoyee(Player player) {
        return demandesEnvoyees.containsKey(player.getUniqueId());
    }

    public UUID getCibleDemande(Player player) {
        return demandesEnvoyees.get(player.getUniqueId());
    }

    public UUID getDemandeurEnAttente(Player player) {
        return demandesRecues.get(player.getUniqueId());
    }

    private ChatColor couleurAleatoire() {
        List<String> palette = Reglages.liste("teams.random-colors");
        if (!palette.isEmpty()) {
            try {
                return ChatColor.valueOf(palette.get(random.nextInt(palette.size())).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ChatColor.WHITE;
    }

    private String prochainNomDistrict() {
        return Reglages.texte("teams.name-format").replace("{n}", String.valueOf(compteurDistrict.incrementAndGet()));
    }

    public void reinitialiserCompteurDistrict() {
        compteurDistrict.set(0);
    }

    public boolean aUneNegociationEnCours(Player player) {
        return propositionsEnCours.containsKey(player.getUniqueId());
    }

    public PropositionEquipe getProposition(Player player) {
        return propositionsEnCours.get(player.getUniqueId());
    }

    public boolean estEnCooldown(Player player) {
        Long fin = cooldownRefus.get(player.getUniqueId());
        return fin != null && System.currentTimeMillis() < fin;
    }

    public long getCooldownRestant(Player player) {
        Long fin = cooldownRefus.get(player.getUniqueId());
        if (fin == null) return 0;
        return Math.max(0, fin - System.currentTimeMillis());
    }

    public boolean envoyerDemande(Player demandeur, Player cible) {
        UUID uDemandeur = demandeur.getUniqueId();
        UUID uCible = cible.getUniqueId();

        if (demandeur.equals(cible)) return false;

        if (!estDisponiblePourEquipe(uDemandeur)) return false;
        if (!estDisponiblePourEquipe(uCible)) return false;
        if (estEnCooldown(demandeur)) return false;

        if (MenuClasse.aMenuClasseOuvert(cible)) return false;

        demandesEnvoyees.put(uDemandeur, uCible);
        demandesRecues.put(uCible, uDemandeur);

        cible.playSound(cible.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 1.2f);
        cible.sendMessage(Messages.get("equipemanager.veut_faire_equipe_avec_toi", "demandeur", demandeur.getName()));

        return true;
    }

    public void annulerDemande(Player demandeur) {
        UUID uDemandeur = demandeur.getUniqueId();
        UUID uCible = demandesEnvoyees.remove(uDemandeur);

        if (uCible == null) return;

        demandesRecues.remove(uCible);

        Player cible = Bukkit.getPlayer(uCible);
        if (cible != null) {
            cible.sendMessage(Messages.get("equipemanager.annule_sa_demande", "demandeur", demandeur.getName()));
        }
    }

    public PropositionEquipe accepterDemande(Player cible) {
        UUID uCible = cible.getUniqueId();
        UUID uDemandeur = demandesRecues.remove(uCible);

        if (uDemandeur == null) return null;

        demandesEnvoyees.remove(uDemandeur);

        Player demandeur = Bukkit.getPlayer(uDemandeur);
        if (demandeur == null) {
            cible.sendMessage(Messages.get("equipemanager.le_joueur_est_plus_en_ligne"));
            return null;
        }

        if (!estDisponiblePourEquipe(uDemandeur) || !estDisponiblePourEquipe(uCible)) {
            cible.sendMessage(Messages.get("equipemanager.impossible_est_plus_disponible_il_deja", "demandeur", demandeur.getName()));
            demandeur.sendMessage(Messages.get("equipemanager.ta_demande_vers_expire_un_de", "cible", cible.getName()));
            return null;
        }

        PropositionEquipe proposition = new PropositionEquipe(uDemandeur, uCible);

        proposition.setNom(prochainNomDistrict());
        propositionsEnCours.put(uDemandeur, proposition);
        propositionsEnCours.put(uCible, proposition);

        return proposition;
    }

    public void refuserDemande(Player cible) {
        UUID uCible = cible.getUniqueId();
        UUID uDemandeur = demandesRecues.remove(uCible);

        if (uDemandeur == null) return;

        demandesEnvoyees.remove(uDemandeur);
        cooldownRefus.put(uDemandeur, System.currentTimeMillis() + COOLDOWN_REFUS);

        Player demandeur = Bukkit.getPlayer(uDemandeur);
        if (demandeur != null) {
            demandeur.sendMessage(Messages.get("equipemanager.refuse_ta_demande_equipe_attends_secondes", "cible", cible.getName(), "cooldown_refus", COOLDOWN_REFUS / 1000));
        }

        cible.sendMessage(Messages.get("equipemanager.tu_as_refuse_la_demande"));
    }

    public void annulerProposition(UUID joueur) {
        PropositionEquipe proposition = propositionsEnCours.remove(joueur);
        if (proposition == null) return;

        UUID autre = proposition.getAutre(joueur);
        propositionsEnCours.remove(autre);

        Player joueurEnLigne = Bukkit.getPlayer(autre);
        if (joueurEnLigne != null) {
            joueurEnLigne.sendMessage(Messages.get("equipemanager.la_configuration_equipe_ete_annulee"));
        }
    }

    public void finaliserEquipe(PropositionEquipe proposition) {
        propositionsEnCours.remove(proposition.getJoueur1());
        propositionsEnCours.remove(proposition.getJoueur2());

        Player joueur1 = Bukkit.getPlayer(proposition.getJoueur1());
        Player joueur2 = Bukkit.getPlayer(proposition.getJoueur2());

        if (joueur1 == null || joueur2 == null) {
            return;
        }

        creerEquipeReelle(joueur1, joueur2, proposition.getNom(), proposition.getCouleur());
    }

    public void formerEquipeAleatoire(Player joueur1, Player joueur2) {
        ChatColor couleur = couleurAleatoire();
        String nom = prochainNomDistrict();

        creerEquipeReelle(joueur1, joueur2, nom, couleur);
    }

    public void formerEquipeSolo(Player joueur) {
        ChatColor couleur = couleurAleatoire();
        String nom = prochainNomDistrict();

        Team team = creerTeam(nom, couleur);
        String nomEquipe = team.getName();
        team.addEntry(joueur.getName());

        equipesSolo.add(joueur.getUniqueId());
        nomEquipeDe.put(joueur.getUniqueId(), nomEquipe);
        teamKeyParNomLower.put(nom.toLowerCase(Locale.ROOT), nomEquipe);
        nomAfficheParTeamKey.put(nomEquipe, nom);

        Bukkit.broadcastMessage(Messages.get("equipemanager.equipe_solo_creee_aucun_partenaire_disponible", "couleur", couleur, "nom", nom, "joueur", joueur.getName()));
        Titre.enFile(joueur, Messages.get("equipemanager.couleur_nom", "couleur", couleur, "nom", nom), Messages.get("equipemanager.equipe_solo_aucun_partenaire_disponible"), 10, 50, 20);

        for (BiConsumer<Player, Player> ecouteur : ecouteursFormation) {
            ecouteur.accept(joueur, joueur);
        }
    }

    public boolean creerEquipeSoloAvecNom(Player joueur, String nom, ChatColor couleur) {
        if (aUneEquipe(joueur)) {
            return false;
        }

        String nomLower = nom.toLowerCase(Locale.ROOT);
        if (teamKeyParNomLower.containsKey(nomLower)) {
            return false;
        }

        Team team = creerTeam(nom, couleur);
        String nomEquipe = team.getName();
        team.addEntry(joueur.getName());

        equipesSolo.add(joueur.getUniqueId());
        nomEquipeDe.put(joueur.getUniqueId(), nomEquipe);
        teamKeyParNomLower.put(nomLower, nomEquipe);
        nomAfficheParTeamKey.put(nomEquipe, nom);

        Bukkit.broadcastMessage(Messages.get("equipemanager.equipe_creee_par_un_admin", "couleur", couleur, "nom", nom, "joueur", joueur.getName()));
        Titre.enFile(joueur, Messages.get("equipemanager.couleur_nom", "couleur", couleur, "nom", nom), Messages.get("equipemanager.equipe_creee_par_un_admin_2"), 10, 50, 20);

        for (BiConsumer<Player, Player> ecouteur : ecouteursFormation) {
            ecouteur.accept(joueur, joueur);
        }

        return true;
    }

    public enum ResultatAjout { SUCCES, JOUEUR_DEJA_EN_EQUIPE, EQUIPE_INTROUVABLE, EQUIPE_DEJA_COMPLETE }

    public List<String> getNomsEquipesSolo() {
        List<String> noms = new ArrayList<>();
        for (UUID uuid : equipesSolo) {
            if (Bukkit.getPlayer(uuid) == null) continue;

            String teamKey = nomEquipeDe.get(uuid);
            String nom = teamKey != null ? nomAfficheParTeamKey.get(teamKey) : null;
            if (nom != null) {
                noms.add(nom);
            }
        }
        return noms;
    }

    public ChatColor getCouleurEquipeSolo(String nomEquipe) {
        String teamKey = teamKeyParNomLower.get(nomEquipe.toLowerCase(Locale.ROOT));
        if (teamKey == null) {
            return null;
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamKey);
        return team != null ? team.getColor() : null;
    }

    public ResultatAjout ajouterAEquipeExistante(Player joueur, String nomEquipe) {
        if (aUneEquipe(joueur)) {
            return ResultatAjout.JOUEUR_DEJA_EN_EQUIPE;
        }

        String teamKey = teamKeyParNomLower.get(nomEquipe.toLowerCase(Locale.ROOT));
        if (teamKey == null) {
            return ResultatAjout.EQUIPE_INTROUVABLE;
        }

        UUID soloUuid = null;
        for (Map.Entry<UUID, String> entree : nomEquipeDe.entrySet()) {
            if (entree.getValue().equals(teamKey) && equipesSolo.contains(entree.getKey())) {
                soloUuid = entree.getKey();
                break;
            }
        }

        if (soloUuid == null) {
            return ResultatAjout.EQUIPE_DEJA_COMPLETE;
        }

        Player soloPlayer = Bukkit.getPlayer(soloUuid);
        if (soloPlayer == null) {
            return ResultatAjout.EQUIPE_INTROUVABLE;
        }

        equipesSolo.remove(soloUuid);
        partenaireDe.put(soloUuid, joueur.getUniqueId());
        partenaireDe.put(joueur.getUniqueId(), soloUuid);
        nomEquipeDe.put(joueur.getUniqueId(), teamKey);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(teamKey);
        if (team != null) {
            team.addEntry(joueur.getName());
        }

        Bukkit.broadcastMessage(Messages.get("equipemanager.rejoint_equipe", "joueur", joueur.getName(), "nomequipe", nomEquipe));

        for (BiConsumer<Player, Player> ecouteur : ecouteursFormation) {
            ecouteur.accept(soloPlayer, joueur);
        }

        return ResultatAjout.SUCCES;
    }

    private Team creerTeam(String nom, ChatColor couleur) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.registerNewTeam(PREFIXE_EQUIPE + compteurEquipe.incrementAndGet());
        team.setColor(couleur);
        // À false, les chiens héritent de la team du maître et deviennent invulnérables. Voir onDegatsEntreCoequipiers.
        team.setAllowFriendlyFire(true);
        team.setPrefix(couleur + "[" + nom + "] ");
        return team;
    }

    private void supprimerTeam(String teamKey) {
        teamKeyParNomLower.values().remove(teamKey);
        nomAfficheParTeamKey.remove(teamKey);
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamKey);
        if (team != null) {
            team.unregister();
        }
    }

    private void creerEquipeReelle(Player joueur1, Player joueur2, String nom, ChatColor couleur) {
        Team team = creerTeam(nom, couleur);
        String nomEquipe = team.getName();
        team.addEntry(joueur1.getName());
        team.addEntry(joueur2.getName());

        partenaireDe.put(joueur1.getUniqueId(), joueur2.getUniqueId());
        partenaireDe.put(joueur2.getUniqueId(), joueur1.getUniqueId());
        nomEquipeDe.put(joueur1.getUniqueId(), nomEquipe);
        nomEquipeDe.put(joueur2.getUniqueId(), nomEquipe);
        teamKeyParNomLower.put(nom.toLowerCase(Locale.ROOT), nomEquipe);
        nomAfficheParTeamKey.put(nomEquipe, nom);

        Bukkit.broadcastMessage(Messages.get("equipemanager.nouvelle_equipe_creee", "couleur", couleur, "nom", nom, "joueur1", joueur1.getName(), "joueur2", joueur2.getName()));

        Titre.enFile(joueur1, Messages.get("equipemanager.couleur_nom", "couleur", couleur, "nom", nom), Messages.get("equipemanager.equipe_formee"), 10, 50, 20);
        Titre.enFile(joueur2, Messages.get("equipemanager.couleur_nom", "couleur", couleur, "nom", nom), Messages.get("equipemanager.equipe_formee"), 10, 50, 20);

        for (BiConsumer<Player, Player> ecouteur : ecouteursFormation) {
            ecouteur.accept(joueur1, joueur2);
        }
    }

    public void retirerEquipe(Player player) {
        UUID uuid = player.getUniqueId();

        if (equipesSolo.remove(uuid)) {
            String nomEquipeSolo = nomEquipeDe.remove(uuid);
            if (nomEquipeSolo != null) {
                supprimerTeam(nomEquipeSolo);
            }
            player.sendMessage(Messages.get("equipemanager.ton_equipe_solo_ete_dissoute"));
            return;
        }

        UUID partenaireUuid = partenaireDe.remove(uuid);

        if (partenaireUuid == null) {
            return;
        }

        partenaireDe.remove(partenaireUuid);

        String nomEquipe = nomEquipeDe.remove(uuid);
        nomEquipeDe.remove(partenaireUuid);

        if (nomEquipe != null) {
            supprimerTeam(nomEquipe);
        }

        player.sendMessage(Messages.get("equipemanager.ton_equipe_ete_dissoute"));

        Player partenaire = Bukkit.getPlayer(partenaireUuid);
        if (partenaire != null) {
            partenaire.sendMessage(Messages.get("equipemanager.ton_equipe_ete_dissoute"));
        }
    }

    public void quitterEquipe(Player joueur) {
        UUID uuid = joueur.getUniqueId();

        if (equipesSolo.remove(uuid)) {
            String nomEquipeSolo = nomEquipeDe.remove(uuid);
            if (nomEquipeSolo != null) {
                supprimerTeam(nomEquipeSolo);
            }
            joueur.sendMessage(Messages.get("equipemanager.ton_equipe_ete_supprimee_tu_etais"));
            return;
        }

        UUID partenaireUuid = partenaireDe.remove(uuid);
        if (partenaireUuid == null) {
            return;
        }
        partenaireDe.remove(partenaireUuid);

        String nomEquipe = nomEquipeDe.remove(uuid);

        equipesSolo.add(partenaireUuid);

        if (nomEquipe != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(nomEquipe);
            if (team != null) {
                team.removeEntry(joueur.getName());
            }
        }

        joueur.sendMessage(Messages.get("equipemanager.tu_as_quitte_ton_equipe"));

        Player partenaire = Bukkit.getPlayer(partenaireUuid);
        if (partenaire != null) {
            partenaire.sendMessage(Messages.get("equipemanager.quitte_equipe_tu_es_maintenant_en", "joueur", joueur.getName()));
        }
    }

    public void quitterEquipeParUuid(UUID uuid, String nomAffiche) {
        if (equipesSolo.remove(uuid)) {
            String nomEquipeSolo = nomEquipeDe.remove(uuid);
            if (nomEquipeSolo != null) {
                supprimerTeam(nomEquipeSolo);
            }
            return;
        }

        UUID partenaireUuid = partenaireDe.remove(uuid);
        if (partenaireUuid == null) {
            return;
        }
        partenaireDe.remove(partenaireUuid);

        String nomEquipe = nomEquipeDe.remove(uuid);
        equipesSolo.add(partenaireUuid);

        if (nomEquipe != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(nomEquipe);
            if (team != null) {
                team.removeEntry(nomAffiche);
            }
        }

        Player partenaire = Bukkit.getPlayer(partenaireUuid);
        if (partenaire != null) {
            partenaire.sendMessage(Messages.get("equipemanager.quitte_le_match_deconnexion_tu_es", "nomaffiche", nomAffiche));
        }
    }

    public Team getTeamCosmetique(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getEntryTeam(player.getName());

        if (team == null) {
            return null;
        }

        if (team.getName().startsWith(PREFIXE_EQUIPE) || team.getName().startsWith("role_")) {
            return team;
        }

        return null;
    }

    public void restaurerTeamCosmetique(Player player, Team team) {
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    @EventHandler
    public void onDegatsEntreCoequipiers(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victime)) return;

        Player attaquant = resoudreAttaquant(event.getDamager());
        if (attaquant == null) return;

        UUID partenaire = partenaireDe.get(attaquant.getUniqueId());

        if (partenaire != null && partenaire.equals(victime.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public static Player resoudreAttaquant(Entity damager) {
        if (damager instanceof Player joueur) {
            return joueur;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player tireur) {
            return tireur;
        }
        return null;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        nettoyer(event.getPlayer());
    }

    public void nettoyer(Player player) {
        UUID uuid = player.getUniqueId();

        annulerDemande(player);

        UUID demandeurEnAttente = demandesRecues.remove(uuid);
        if (demandeurEnAttente != null) {
            demandesEnvoyees.remove(demandeurEnAttente);
            Player demandeur = Bukkit.getPlayer(demandeurEnAttente);
            if (demandeur != null) {
                demandeur.sendMessage(Messages.get("equipemanager.est_deconnecte_ta_demande_est_annulee", "joueur", player.getName()));
            }
        }

        if (propositionsEnCours.containsKey(uuid)) {
            annulerProposition(uuid);
        }

        cooldownRefus.remove(uuid);
    }
}
