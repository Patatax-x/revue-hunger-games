# Hunger Games

Plugin Paper de Hunger Games par équipes de 2 : classes à pouvoirs, coffres de loot, bordure qui rétrécit, largages et détection automatique de la victoire.

Développé par Potato Studio.

## Versions

- Minecraft / Paper 1.21.4
- Java 21 ou plus récent

## Installation

1. Copier `HungerGame-<version>.jar` dans `plugins/`, puis démarrer le serveur une fois pour générer la configuration.
2. Placer chaque monde de jeu à la racine du serveur et le déclarer dans la section `maps` de `plugins/HungerGame/config.yml`.
3. Redémarrer. Les mondes sont chargés automatiquement (`load-on-start`). Multiverse-Core reste possible mais n'est pas nécessaire.

[FancyHolograms](https://modrinth.com/plugin/fancyholograms) est facultatif : il n'est utilisé que par `/classeholo`.

## Configuration

Le plugin génère deux fichiers. Un redémarrage est nécessaire pour les prendre en compte, sauf le loot (`/loot recharger`).

**`config.yml`**

| Section | Contenu |
| --- | --- |
| `lobby` | Point d'apparition du lobby |
| `match` | Minimums de joueurs et d'équipes, compte à rebours, délai de fin, top kills |
| `border` | Taille finale, dégâts, avertissement, vitesses de `/bordure` et `/max` |
| `teams` | Format du nom automatique, couleurs, délai après un refus, réapparition |
| `combat-tag` | Règles de déconnexion en combat |
| `airdrops` | Rayon du largage manuel |
| `classes` | Recharge des pouvoirs, durée de la prime |
| `reset` | Réinitialisation automatique après une partie |
| `map-defaults` | Coffres, raretés, délai de bordure, durée maximale et largages communs à toutes les maps |
| `maps` | Une section par monde : nom, icône, centre, rayons, sauvegarde. Peut redéfinir toute valeur de `map-defaults` |
| `loot` | Nombre d'objets par coffre, objets uniques, contenu des pools |

Une ligne de pool s'écrit `MATERIAU quantité [enchantement:niveau ...] [potion:type]`, par exemple `DIAMOND_SWORD 1 sharpness:2` ou `SPLASH_POTION 1 potion:poison`. `/loot` permet aussi de capturer un coffre en jeu : la capture, stockée dans `chest-items.yml` avec le NBT complet des objets, remplace le pool correspondant.

`/config` modifie les coffres et les durées d'une map en jeu et écrit dans `config.yml`.

**`messages.yml`** contient tous les textes. Les couleurs s'écrivent avec `&`, les valeurs dynamiques entre accolades (`{joueur}`). Les durées citées dans les textes, comme le livre de classe (« Recharge : 3 minutes »), sont à adapter à la main si vous changez `config.yml`.

Les équipes sont limitées à 2 joueurs. Les classes (nombre, pouvoirs) sont définies dans le code.

## Déroulé d'une partie

1. **`/role add admin <joueur>`** : les admins reçoivent leur trousse d'outils et ne jouent pas. `afk` exclut aussi un joueur de la partie.
2. **`/mode` → LOBBY** : chaque joueur reçoit les objets pour choisir sa classe et son équipe.
3. **`/mode` → FERMETURE** : les joueurs restants sont appariés au hasard, les classes manquantes sont demandées.
4. **`/go`** : choix de la map. Les équipes sont placées en cercle, les coffres apparaissent, puis un compte à rebours gèle tout le monde.
5. **Partie** : le PVP s'active. La bordure rétrécit après son délai et des largages tombent régulièrement. Un joueur mort réapparaît près de son coéquipier si celui-ci survit au délai ; sinon, il passe spectateur.
6. **Fin** : dernière équipe en vie, durée maximale atteinte ou `/end`. Le top kills s'affiche, puis tout le monde revient au lobby.

Pour tester : `/go test` autorise une partie avec une seule équipe (sans détection de victoire), et `/go test_duo` oppose deux joueurs connectés avec le flux normal.

## Commandes et permissions

Toutes les permissions valent `op` par défaut.

| Commande | Permission | Rôle |
| --- | --- | --- |
| `/hunger` | `hungerGame.hunger` | Vue d'ensemble de l'état du jeu |
| `/mode [lobby\|off <joueur>]` | `hungerGame.mode` | Change le mode, ou resynchronise un joueur |
| `/role <add\|remove> <admin\|afk\|tous> <joueur\|@a>`, `/role list <joueur\|@a>` | `hungerGame.role` | Gère les rôles |
| `/classe [leave <joueur>]` | `hungerGame.classe` | Ouvre le choix de classe ou retire une classe |
| `/equipe [create <joueur> <nom> <couleur>\|add <joueur> <équipe>\|leave <joueur>]` | `hungerGame.equipe` | Ouvre le choix d'équipe ou gère les équipes à la main |
| `/verif` | `hungerGame.verif` | Force le choix d'équipe puis de classe |
| `/go [test\|test_duo]` | `hungerGame.go` | Lance la partie |
| `/end` | `hungerGame.end` | Clôture la partie |
| `/pvp <on\|off>` | `hungerGame.pvp` | Active ou coupe le PVP sur la map active |
| `/bordure <normal\|lent\|eleve\|treseleve\|megaeleve\|secondes>` | `hungerGame.bordure` | Change la vitesse de la bordure |
| `/max` | `hungerGame.max` | Rétrécit la bordure à la vitesse maximale |
| `/largage` | `hungerGame.largage` | Déclenche un largage |
| `/config [nettoyer]` | `hungerGame.config` | Règle les coffres et durées par map, ou retire les coffres posés |
| `/loot [outil\|vider\|recharger\|liste\|defaut]` | `hungerGame.loot` | Gère les pools de loot |
| `/resetmap [<map>\|auto <on\|off>]` | `hungerGame.resetmap` | Restaure une map depuis son `backup-folder` |
| `/vanish` | `hungerGame.vanish` | Rend invisible aux joueurs |
| `/pnj [kill]` | `hungerGame.pnj` | Crée ou retire des mannequins d'entraînement |
| `/classeholo` | `hungerGame.classeholo` | Donne les œufs qui posent les hologrammes de classe |

## Compilation

Java 21 et Maven sont nécessaires.

```bash
mvn package
```

Les tests sont lancés pendant le build, et le jar est produit dans `target/`.
