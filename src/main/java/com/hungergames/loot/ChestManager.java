package com.hungergames.loot;

import com.hungergames.config.Messages;
import com.hungergames.hud.ActionBar;
import com.hungergames.map.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ChestManager implements Listener {
    private static final double ESPACEMENT_MIN_CLUSTER = 2.5;
    private static final double ESPACEMENT_MIN_EPARPILLE = 4.0;
    private static final int RAYON_CLUSTER_MIN = 8;
    private static final int TENTATIVES_MAX = 40;

    private static final long INTERVALLE_PARTICULES = 10L;

    private static final int POINTS_HALO = 16;
    private static final double RAYON_HALO = 0.7;
    private static final double[] HAUTEURS_HALO = {0.3, 0.85};

    private final Map<GameMap, ConfigMap> configs = new HashMap<>();
    private final Map<GameMap, List<Location>> coffresPoses = new HashMap<>();
    private final Random random = new Random();
    private final LootRegistry lootRegistry;

    private final NamespacedKey cleRarete;

    public ChestManager(Plugin plugin, LootRegistry lootRegistry) {
        this.lootRegistry = lootRegistry;
        this.cleRarete = new NamespacedKey(plugin, "loot_rarete");
        Bukkit.getScheduler().runTaskTimer(plugin, this::genererParticulesCoffres, 0L, INTERVALLE_PARTICULES);
    }

    public ConfigMap getConfig(GameMap map) {
        return configs.computeIfAbsent(map, ConfigMap::new);
    }

    public void regenererCoffres(World monde, GameMap map) {
        nettoyerCoffres(map);

        ConfigMap config = getConfig(map);
        List<Location> poses = new ArrayList<>();

        int rayonMinPourLeCompte = (int) Math.ceil(Math.sqrt(config.getNombreCoffresCentre()) * 2.2);
        int rayonCluster = Math.max(config.getRayonCluster(), Math.max(RAYON_CLUSTER_MIN, rayonMinPourLeCompte));

        for (int i = 0; i < config.getNombreCoffresCentre(); i++) {
            poserUnCoffre(monde, config, config.getCentreClusterX(), config.getCentreClusterY(), config.getCentreClusterZ(), rayonCluster, true, ESPACEMENT_MIN_CLUSTER, poses);
        }

        for (int i = 0; i < config.getNombreCoffresEparpilles(); i++) {
            poserUnCoffre(monde, config, config.getCentreX(), config.getCentreY(), config.getCentreZ(), config.getRayonEparpillement(), false, ESPACEMENT_MIN_EPARPILLE, poses);
        }

        coffresPoses.put(map, poses);
    }

    public Location declencherLargagePres(World monde, GameMap map, Location origine, double rayon) {
        return declencherLargageAutour(monde, map, origine.getX(), origine.getY(), origine.getZ(), rayon);
    }

    private Location declencherLargageAutour(World monde, GameMap map, double centreX, double centreY, double centreZ, double rayon) {
        List<Location> poses = coffresPoses.computeIfAbsent(map, m -> new ArrayList<>());

        Location sol = positionAleatoireValide(monde, centreX, centreY, centreZ, rayon, false, ESPACEMENT_MIN_EPARPILLE, poses);
        if (sol == null) {
            return null;
        }

        Location emplacementCoffre = sol.clone().add(0.5, 1, 0.5);
        emplacementCoffre.getBlock().setType(Material.CHEST);

        if (!(emplacementCoffre.getBlock().getState() instanceof Container conteneur)) {
            return null;
        }

        etiqueterRarete(conteneur, Rarete.LARGAGE);

        int varianteLargage = 1 + random.nextInt(Rarete.LARGAGE.getNombreVariantes());
        remplirCoffreDisperse(conteneur.getInventory(),
                LootTables.genererContenu(Rarete.LARGAGE, varianteLargage, lootRegistry));

        poses.add(emplacementCoffre);

        return emplacementCoffre;
    }

    public void nettoyerFinDePartie(World monde, GameMap map) {
        nettoyerCoffres(map);

        for (Item item : monde.getEntitiesByClass(Item.class)) {
            item.remove();
        }
    }

    public int nettoyerToutesLesMaps() {
        int total = 0;

        for (GameMap map : GameMap.values()) {
            World monde = Bukkit.getWorld(map.getCle());
            if (monde == null) continue;

            total += nettoyerCoffres(map);
            total += nettoyerCoffresNonSuivis(monde);

            for (Item item : monde.getEntitiesByClass(Item.class)) {
                item.remove();
            }
        }

        return total;
    }

    private int nettoyerCoffresNonSuivis(World monde) {
        int retires = 0;

        for (Chunk chunk : monde.getLoadedChunks()) {
            for (BlockState etat : chunk.getTileEntities()) {
                if (rareteDuCoffre(etat.getBlock()) != null) {
                    etat.getBlock().setType(Material.AIR);
                    retires++;
                }
            }
        }

        return retires;
    }

    @EventHandler
    public void onCasseConteneurLoot(BlockBreakEvent event) {
        if (rareteDuCoffre(event.getBlock()) != null) {
            event.setCancelled(true);
            ActionBar.prioritaire(event.getPlayer(), Messages.get("chestmanager.impossible_de_casser_un_coffre_de"));
        }
    }

    private int nettoyerCoffres(GameMap map) {
        List<Location> anciens = coffresPoses.get(map);
        if (anciens == null) {
            return 0;
        }

        int retires = 0;
        for (Location loc : anciens) {
            Block block = loc.getBlock();

            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
                retires++;
            }
        }

        anciens.clear();
        return retires;
    }

    private void etiqueterRarete(Container conteneur, Rarete rarete) {
        if (conteneur instanceof TileState etat) {
            etat.getPersistentDataContainer().set(cleRarete, PersistentDataType.STRING, rarete.name());
            etat.update();
        }
    }

    private Rarete rareteDuCoffre(Block block) {
        if (block.getType() != Material.CHEST) {
            return null;
        }
        if (!(block.getState() instanceof TileState etat)) {
            return null;
        }
        String nom = etat.getPersistentDataContainer().get(cleRarete, PersistentDataType.STRING);
        if (nom == null) {
            return null;
        }
        try {
            return Rarete.valueOf(nom);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void genererParticulesCoffres() {
        for (List<Location> poses : coffresPoses.values()) {
            for (Location emplacement : poses) {
                Rarete rarete = rareteDuCoffre(emplacement.getBlock());
                if (rarete != null) {
                    afficherHaloParticules(emplacement, rarete.getCouleurParticule());
                }
            }
        }
    }

    private void afficherHaloParticules(Location emplacementCoffre, Color couleur) {
        World monde = emplacementCoffre.getWorld();
        if (monde == null) {
            return;
        }

        Particle.DustOptions options = new Particle.DustOptions(couleur, 1.3f);
        double baseX = emplacementCoffre.getX();
        double baseZ = emplacementCoffre.getZ();

        for (double hauteur : HAUTEURS_HALO) {
            double y = emplacementCoffre.getY() + hauteur;
            for (int i = 0; i < POINTS_HALO; i++) {
                double angle = (2 * Math.PI / POINTS_HALO) * i;
                double px = baseX + RAYON_HALO * Math.cos(angle);
                double pz = baseZ + RAYON_HALO * Math.sin(angle);
                monde.spawnParticle(Particle.DUST, px, y, pz, 1, 0, 0, 0, 0, options);
            }
        }
    }

    private void poserUnCoffre(World monde, ConfigMap config, double centreX, double centreY, double centreZ, double rayon, boolean hauteurFixe, double espacementMin, List<Location> poses) {
        Location sol = positionAleatoireValide(monde, centreX, centreY, centreZ, rayon, hauteurFixe, espacementMin, poses);
        if (sol == null) {
            return;
        }

        Rarete rarete = config.tirerRarete(random);
        int variante = 1 + random.nextInt(rarete.getNombreVariantes());

        Location emplacementCoffre = sol.clone().add(0.5, 1, 0.5);
        emplacementCoffre.getBlock().setType(Material.CHEST);

        if (!(emplacementCoffre.getBlock().getState() instanceof Container conteneur)) {
            return;
        }

        etiqueterRarete(conteneur, rarete);
        remplirCoffreDisperse(conteneur.getInventory(), LootTables.genererContenu(rarete, variante, lootRegistry));

        poses.add(emplacementCoffre);
    }

    // addItem remplit toujours depuis le slot 0 : des slots tirés au hasard donnent un coffre d'aspect naturel.
    private void remplirCoffreDisperse(Inventory inventaire, List<ItemStack> items) {
        List<Integer> slotsLibres = new ArrayList<>();
        for (int slot = 0; slot < inventaire.getSize(); slot++) {
            if (inventaire.getItem(slot) == null) {
                slotsLibres.add(slot);
            }
        }
        Collections.shuffle(slotsLibres, random);

        int i = 0;
        for (; i < items.size() && i < slotsLibres.size(); i++) {
            inventaire.setItem(slotsLibres.get(i), items.get(i));
        }
        for (; i < items.size(); i++) {
            inventaire.addItem(items.get(i));
        }
    }

    private Location positionAleatoireValide(World monde, double centreX, double centreY, double centreZ, double rayon, boolean hauteurFixe, double espacementMin, List<Location> poses) {
        for (int tentative = 0; tentative < TENTATIVES_MAX; tentative++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * rayon;

            double x = centreX + distance * Math.cos(angle);
            double z = centreZ + distance * Math.sin(angle);

            int solY = hauteurFixe
                    ? (int) Math.round(centreY) - 1
                    : monde.getHighestBlockYAt((int) Math.round(x), (int) Math.round(z));

            Location sol = new Location(monde, Math.round(x), solY, Math.round(z));

            if (positionValide(sol, espacementMin, poses)) {
                return sol;
            }
        }

        return null;
    }

    private boolean positionValide(Location sol, double espacementMin, List<Location> poses) {
        Block blocSol = sol.getBlock();
        Block auDessus1 = blocSol.getRelative(0, 1, 0);
        Block auDessus2 = blocSol.getRelative(0, 2, 0);

        if (!blocSol.getType().isSolid()) return false;
        if (blocSol.isLiquid()) return false;
        if (auDessus1.getType() != Material.AIR || auDessus2.getType() != Material.AIR) return false;

        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            Material voisin = auDessus1.getRelative(face).getType();
            if (voisin == Material.CHEST || voisin == Material.TRAPPED_CHEST) {
                return false;
            }
        }

        for (Location autre : poses) {
            if (autre.getWorld() == sol.getWorld() && autre.distance(sol) < espacementMin) {
                return false;
            }
        }

        return true;
    }
}
