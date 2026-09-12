# Push – Mini-jeu PvP en arène pour Paper/Spigot

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://adoptium.net)
[![Paper/Spigot](https://img.shields.io/badge/Paper/Spigot-1.21.4-blueviolet)](https://papermc.io)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Release](https://img.shields.io/github/v/release/herocraftlol/Push)](https://github.com/herocraftlol/Push/releases/latest)

Push est un plugin **Paper/Spigot 1.21.x** qui ajoute un **mini-jeu de type PvP par équipes** : vous créez vos propres arènes, vous lâchez les joueurs au-dessus d'un **espace de vide**, et la première équipe qui pousse ses adversaires dans le trou **5 fois** remporte la partie.

Le plugin s'occupe de tout : matchmaking, kits, scoreboard, stats, leaderboard, cosmétiques, mode spectateur, hologrammes de classement… Il a été pensé pour être rapide à mettre en place (100 % configurable **en jeu** avec `/p …`) et agréable à regarder jouer, que l'on soit joueur ou spectateur.

> 🎮 Idéal pour les serveurs **HikaBrain-like**, les mini-jeux compétitifs entre amis, les tournois communautaires, ou simplement pour animer un monde survie.

---

## 📥 Téléchargement

La dernière build stable est disponible dans la
[page des Releases](https://github.com/herocraftlol/Push/releases/latest) :
téléchargez **`Push-1.0.4.jar`** et placez-le dans le dossier `plugins/` de
votre serveur Paper **1.21.x**, puis redémarrez le serveur.

> Pour installer la version de développement (code source) : clonez le dépôt
> et lancez `mvn clean package` — voir la section [Compilation](#%EF%B8%8F-compilation)
> plus bas.

---

## ✨ Ce que fait le plugin

Push transforme vos simples « trous au-dessus du vide » en véritables arènes
PvP structurées, avec tout ce qu'il faut pour que les parties s'enchaînent
sans accroc.

### Côté gameplay

- **Arènes multiples nommées** : créez autant d'arènes que vous voulez, elles
  peuvent tourner **en parallèle** sur le même serveur.
- **2 à 4 équipes par arène** : les équipes sont configurables en couleur et
  en nom (`Magenta`, `Rose`, `Violet`, `Bleu foncé` par défaut).
- **1 à 8 joueurs par équipe** : vous choisissez la taille des équipes, et vous
  pouvez même définir **plusieurs spawns par équipe** pour éviter que tout le
  monde apparaisse au même point.
- **Lobby d'attente avec countdown** : la partie se lance automatiquement
  dès que le minimum de joueurs est atteint, avec un compte à rebours de 30 s,
  **annulable** si un joueur quitte.
- **Diamant admin** : pour les admins, un diamant spécial en main pendant
  l'attente permet de forcer le démarrage instantané.
- **Kit fixe et verrouillé** : épée en pierre, arc à enchantement **INFINITY**
  (flèche infinie), armure en cuir teintée à la couleur de l'équipe. Tout est
  **incassable**, non déplaçable, non échangeable, non droppable.
- **Sécurité arc/flèches** : un watchdog redonne l'arc ou les flèches à un
  joueur qui les aurait perdus — **on ne peut jamais se retrouver sans tir**.
- **Recharge visuelle** : cooldown de 3 s configurable, géré nativement par
  Minecraft, avec barre d'action et sidebar.
- **Éliminations PvP ET par le vide** : un kill direct rapporte un point à
  l'équipe du tueur ; une chute sous la limite de vide donne le point à
  l'équipe adverse (ou au dernier toucheur si identifiable).
- **Pause entre les manches** : après chaque point, tout le monde est
  retéléporté à sa base, kits retirés, et un compte à rebours de 3-5 s
  s'affiche avant la reprise.
- **Victoire à 5 points** (configurable) : à la fin, tous les joueurs passent
  en spectateur, l'équipe gagnante est annoncée, puis chacun est téléporté
  exactement là où il était avant de rejoindre le lobby.
- **Salle d'attente qui disparaît pendant les parties** : construisez votre
  salle d'attente **où vous voulez**, le plugin prend un « moule » de la
  structure, l'efface au lancement de la partie et la repose à l'identique à
  la réouverture. Les blocs plus loin dans la zone ne sont jamais touchés par
  erreur. Voir la section dédiée plus bas.

### Côté expérience joueur

- **Sidebar détaillé par joueur** : rafraîchi chaque seconde, décliné en
  variante *lobby* / *partie* / *spectateur* (effectifs, scores, K/D, dégâts,
  état de l'arc…). Pseudos colorés à la couleur de l'équipe dans la tab list
  et au-dessus de la tête.
- **Messages rapides multilingues** : pendant la pause ou à l'écran de victoire,
  quatre blocs colorés permettent d'envoyer des messages tels que *« Bien
  joué ! »*, *« Tu es trop fort ! »*, *« J'aurai ma revanche ! »*, *« Même pas
  peur ! »* — **traduits automatiquement** dans la langue de chaque
  destinataire (FR, EN, ES, DE, PT, RU, IT, NL, PL, TR, ZH, JA, KO).
- **Mode spectateur configurable** : `/p spectate <arène>` permet d'observer
  une partie en mode SPECTATOR, avec vision nocturne, vol autorisé, retour à
  la position d'origine sur `/p unspectate`. Activable/désactivable arène par
  arène avec `/p spectatormode <nom> <on|off>`.
- **Cosmétiques exclusifs Push** : 7 effets d'élimination purement visuels
  (Étincelles, Flammes, Explosion, Foudre, Feu d'artifice, Étoile légendaire…)
  que chaque joueur **déverrouille en montant de niveau** et sélectionne avec
  `/p cosmetics`. Aucun impact sur le gameplay.

### Côté progression & social

- **Système de niveaux** : XP gagnée en tuant (`15`), en poussant dans le vide
  (`20`), en infligeant des dégâts (`0,1/point`), en jouant (`2/min`) et en
  gagnant (`100`). Consultable avec `/p level [joueur]`.
- **Statistiques persistantes** : victoires, éliminations et dégâts de chaque
  joueur, sauvegardés automatiquement (`plugins/Push/stats.yml`).
- **Leaderboard** : classement des meilleurs joueurs avec leurs victoires et
  éliminations, accessible via `/p leaderboard` ou `/p gui`.
- **Hologrammes de classement** : `/p holo summon <kills|pushes|damage|wins|level>`
  pose un **hologramme de top 5** au point visé, rafraîchi automatiquement.
  Gérez-les avec `/p holo list` et `/p holo remove <id>`.

---

## 🆕 Nouveautés de la version 1.0.4

Cette release consolide le plugin autour de plusieurs corrections et
améliorations importantes.

- **🛡️ Bug de respawn corrigé** — sur la manche gagnante (mort au moment
  exact où la partie se termine), le joueur n'était plus toujours téléporté
  correctement. Corrigé.
- **🎨 Couleurs d'équipe corrigées** — l'équipe *Magenta* est désormais bien
  colorée en magenta (`DARK_PURPLE`) et l'équipe *Rose* en rose/clair
  (`LIGHT_PURPLE`) — c'était inversé auparavant.
- **🪶 Sidebar restylé façon HikaBrain** — icônes cœur (❤) par équipe,
  séparateurs en traits horizontaux, icône œil en mode spectateur.
- **🏹 Arc durci** — suppression des flèches fantômes qui pouvaient rester
  visibles quand un tir était annulé (cooldown / pause).
- **⏸️ Pause configurable entre les manches** — 3 secondes (réglable via
  `round-resume-seconds`) entre chaque point marqué.
- **💬 Messages rapides multilingues** — quatre phrases-traduites
  automatiquement dans la langue de chaque destinataire, cliquables sur des
  blocs de couleur pendant les pauses et à l'écran de victoire.
  Désactivables avec `quick-chat.enabled: false`.
- **🛰️ Mode spectateur configurable par arène** — `/p spectatormode <nom> <on|off>`
  pour l'activer/désactiver ; les réglages globaux (vision nocturne, vol,
  visibilité des autres spectateurs) restent dans `config.yml`.
- **📈 Système de niveaux Push** — XP gagnée selon plusieurs critères
  (kills, pushes dans le vide, dégâts, temps de jeu, victoires) et table de
  niveaux stockée sur disque. Visible via `/p level [joueur]`.
- **🎭 7 cosmétiques d'élimination** — Étincelles (niv. 3), Flammes (niv. 6),
  Explosion (niv. 10), Foudre (niv. 15), Feu d'artifice (niv. 20), Étoile
  légendaire (niv. 25) — sélectionnables avec `/p cosmetics`, purement
  visuels/sonores, **aucun impact sur le gameplay**.
- **🏆 Hologrammes de classement** — `/p holo summon` pose un top 5 mis à
  jour automatiquement à votre position, `/p holo list` / `remove` pour
  les gérer.
- **🚧 Salle d'attente qui disparaît pendant les parties** — déjà
  fonctionnelle : capture d'une zone construite en jeu et effacement/pose
  automatique au lancement et à la réouverture d'une partie.

---

## 🚀 Installation rapide

1. Téléchargez **`Push-1.0.4.jar`** depuis la
   [page des Releases](https://github.com/herocraftlol/Push/releases/latest).
2. Placez-le dans le dossier `plugins/` de votre serveur **Paper 1.21.x**.
3. Redémarrez le serveur. Le plugin crée automatiquement :
   - `plugins/Push/config.yml` (paramètres globaux)
   - `plugins/Push/stats.yml` (statistiques des joueurs)
   - `plugins/Push/lobbies/` (moules des salles d'attente)
4. En jeu, donnez-vous la permission `push.admin` (par défaut, tous les **ops**
   l'ont) et configurez au moins une arène — voir
   [Configuration d'une arène](#-configuration-dune-arène-en-jeu).

---

## 🛠️ Configuration d'une arène (en jeu)

Toutes les commandes de configuration nécessitent `push.admin` et, pour les
commandes positionnelles, d'être exécutées par **un joueur** (la position du
joueur au moment de la commande est enregistrée).

1. **Créer l'arène**

   ```mcfunction
   /p create <nom>
   ```

2. **Définir le point du lobby d'attente** (tenez-vous à l'endroit voulu)

   ```mcfunction
   /p setlobby <nom>
   ```

3. **Définir le nombre d'équipes** (entre 2 et 4)

   ```mcfunction
   /p setteams <nom> <2-4>
   ```

4. **Définir le(s) spawn(s) de chaque équipe** (tenez-vous à l'endroit voulu)

   ```mcfunction
   /p setspawn <nom> 1
   /p setspawn <nom> 2
   /p setspawn <nom> 3      # si 3+ équipes
   /p setspawn <nom> 4      # si 4 équipes
   ```

   Avec `teamSize > 1`, lancez simplement `/p setspawn <nom> <equipe>` une
   nouvelle fois depuis un autre point : un nouveau spawn est ajouté à la suite
   (tiré au sort à chaque téléportation). Pour remplacer un spawn précis :

   ```mcfunction
   /p setspawn <nom> 1 2    # (re)définit le spawn n°2 de l'équipe 1
   /p delspawn <nom> 1 2    # supprime le spawn n°2 de l'équipe 1
   ```

   Le menu `/p spawns <nom>` ouvre un GUI visuel : clic gauche pour rappeler
   la commande, clic droit pour supprimer le dernier spawn ajouté.

5. **Définir la limite de vide** (hauteur **Y** en dessous de laquelle un
   joueur est éliminé)

   ```mcfunction
   /p setvoid <nom> [y]
   ```

   Si vous ne précisez pas de valeur, c'est votre position **Y** actuelle qui
   est utilisée — placez-vous juste au niveau du vide, ou légèrement au-dessus
   du fond de l'arène. Exemple : `/p setvoid arene1 50` élimine tout joueur
   descendant sous **Y=50**.

6. **Définir la taille maximale d'une équipe** (entre 1 et 8 joueurs)

   ```mcfunction
   /p setteamsize <nom> <1-8>
   ```

7. **Définir le minimum de joueurs pour démarrer automatiquement** (2 par
   défaut)

   ```mcfunction
   /p setminplayers <nom> <minimum>
   ```

8. **(Optionnel) Définir le point d'observation des spectateurs**

   ```mcfunction
   /p setspectator <nom>
   ```

9. **Vérifier que tout est prêt**

   ```mcfunction
   /p info <nom>
   ```

   La ligne « **Entièrement configurée** » doit afficher **oui**. Au minimum
   il faut : un lobby, une limite de vide, et un spawn par équipe active.

---

## 🎮 Commandes joueur

```mcfunction
/p list              # liste les arènes et leur état
/p info <nom>        # détails d'une arène
/p join <nom>        # rejoindre le lobby d'attente
/p leave             # quitter l'arène (lobby, partie, spectateur)
/p spectate <nom>    # observer une partie en mode spectateur
/p unspectate        # quitter le mode spectateur, retour à la position d'origine
/p stats [joueur]    # affiche les victoires et éliminations (les tiennes par défaut)
/p level [joueur]    # affiche le niveau et l'XP d'un joueur
/p cosmetics         # choisir son effet d'élimination (déverrouillé via le niveau)
/p leaderboard       # top joueurs (victoires / éliminations)
/p gui               # menu graphique : clic gauche = rejoindre, clic droit = spectate
```

---

## 🛡️ Commandes admin supplémentaires

```mcfunction
/p delete <nom>                         # supprimer une arène
/p delspawn <nom> <equipe> <numero>     # supprimer un spawn d'équipe
/p spawns <nom>                         # menu visuel des spawns
/p setspectator <nom>                   # définir le point d'apparition des spectateurs
/p spectatormode <nom> <on|off>         # activer/désactiver le mode spectateur d'une arène
/p <nom> lobbyzone pos1                 # premier coin de la salle d'attente
/p <nom> lobbyzone pos2                 # second coin (la structure est capturée)
/p <nom> lobbyzone save                 # ré-enregistrer après modification
/p <nom> lobbyzone clear                # effacer manuellement la structure
/p <nom> lobbyzone paste                # reposer manuellement la structure
/p <nom> lobbyzone info                 # état de la zone et de la structure
/p holo summon <kills|pushes|damage|wins|level>
                                        # pose un hologramme de top 5 à votre position
/p holo list                            # liste les hologrammes en place
/p holo remove <id>                     # supprime un hologramme
/p forcestart [nom]                     # démarre la partie immédiatement
/p reload                               # recharge la configuration depuis config.yml
```

---

## 🏟️ Salle d'attente qui disparaît pendant les parties

C'est l'une des fonctionnalités signatures de Push : vous construisez votre
salle d'attente **où vous voulez** dans le monde (typiquement juste au-dessus
de la zone de jeu), et le plugin en fait une sauvegarde pour l'effacer au
lancement de chaque partie — **les joueurs ne la voient donc pas pendant
qu'ils combattent**.

1. **Construisez votre salle d'attente** en jeu, à l'endroit de votre choix.
   Définissez le point de lobby de l'arène **à l'intérieur** avec
   `/p setlobby <nom>`.

2. **Visez le premier coin** de la construction et tapez :

   ```mcfunction
   /p <nom> lobbyzone pos1
   ```

3. **Visez le coin opposé** (en diagonale, en incluant la hauteur) et tapez :

   ```mcfunction
   /p <nom> lobbyzone pos2
   ```

   Dès que les deux coins sont posés, la structure est enregistrée et le
   **nombre de blocs capturés** vous est confirmé.

4. **C'est tout.** À chaque lancement de partie la structure disparaît, et
   elle revient dès que l'arène rouvre (fin de partie, arène vidée,
   redémarrage du serveur).

### Précisions utiles

- **Les blocs d'air ne sont pas enregistrés** : seule la construction compte.
- **La capture est refusée pendant une partie** et au-delà de **500 000 blocs**
  de volume.
- **Pose / effacement intelligents** : de bas en haut pour la pose, de haut en
  bas pour l'effacement — pour ne pas casser les torches, dalles, et
  pancartes, et ne pas déclencher de chutes de sable.
- **Traitement par paquets** : les grosses structures sont posées/effacées par
  paquets de 4 000 blocs par tick, pour éviter tout pic de lag.
- **Stockage compact** : les moules sont stockés dans
  `plugins/Push/lobbies/<arène>.yml`, avec une palette de types de blocs pour
  garder des fichiers compacts.
- **Modifs ultérieures** : si vous modifiez ensuite la construction, relancez
  `/p <nom> lobbyzone save` pour mettre le moule à jour. `/p <nom> lobbyzone info`
  affiche l'état courant.

---

## ⚙️ Personnalisation

### Couleurs et noms d'équipe

Dans `config.yml` :

```yaml
team-colors:
  - DARK_PURPLE
  - LIGHT_PURPLE
  - BLUE
  - DARK_BLUE

team-display-names:
  - "Magenta"
  - "Rose"
  - "Violet"
  - "Bleu fonce"
```

> ⚠️ **Note importante** : utilisez les `ChatColor` valides côté Bukkit
> (`LIGHT_PURPLE`, `DARK_PURPLE`, `BLUE`, `DARK_BLUE`, `RED`, `GREEN`,
> `YELLOW`, `AQUA`, `GOLD`…). Bukkit **n'a pas** de `ChatColor` `PINK` ni
> `MAGENTA` (contrairement aux vitres teintées/laines) — `LIGHT_PURPLE` est
> l'équivalent texte le plus proche du magenta/rose.

### Paramètres de partie

```yaml
points-to-win: 5           # points nécessaires pour gagner
bow-cooldown-seconds: 3    # 0 = aucun cooldown
end-delay-seconds: 5       # délai avant téléportation de retour
round-resume-seconds: 3    # pause entre chaque point marqué
```

### Sidebar

```yaml
scoreboard:
  title: "&8[&b&lHERO&d&lCRAFT&8] &d&lPUSH"
  footer: "&dplay.herocraft.fr"
  update-ticks: 20         # 20 ticks = 1 seconde
```

Sur une arène à 4 équipes, le sidebar atteint la **limite de 15 lignes**
imposée par Minecraft : les lignes « Spectateurs » et « Dégâts » sont alors
automatiquement masquées, plutôt que de tronquer la fin.

---

## 🔑 Permissions

| Permission       | Effet                                                                  | Défaut |
|------------------|------------------------------------------------------------------------|--------|
| `push.admin`     | Configurer les arènes, forcer le démarrage, diamant admin             | `op`   |
| `push.play`      | Rejoindre une arène                                                     | tous   |
| `push.spectate`  | Observer une partie en mode spectateur                                 | tous   |
| `push.use`       | Utiliser les commandes de base (`/p list`, `info`, `join`, `leave`, …) | tous   |

---

## 🛠️ Compilation

Ce projet est un projet **Maven** standard. Sur votre machine (avec
**JDK 21** et **Maven** installés) :

```bash
git clone https://github.com/herocraftlol/Push.git
cd Push
mvn clean package
```

Le fichier `target/Push-1.0.4.jar` est généré : placez-le dans le dossier
`plugins/` de votre serveur Paper **1.21.x**, puis redémarrez le serveur.

> Le projet télécharge automatiquement l'API Paper 1.21.4 depuis le dépôt
> officiel `repo.papermc.io` au moment du build (dépendance `provided`,
> donc elle n'est **pas** incluse dans le jar final — c'est normal et
> attendu).

---

## 📋 Notes techniques

- Les arènes peuvent fonctionner **simultanément** sur le même serveur
  (différents joueurs dans différentes arènes en même temps).
- La configuration (spawns, lobby, limite de vide, équipes, moules de
  salle) est **persistée automatiquement** dans `config.yml` et
  `plugins/Push/lobbies/` à chaque modification et au moment de l'arrêt du
  serveur.
- Le respawn après une mort en partie replace le joueur **directement au
  spawn de son équipe**, avec un kit neuf, sans interruption de la partie.
- **Chaque arène utilise son propre scoreboard** (sidebar + équipes
  colorées), isolé des autres arènes et du scoreboard principal du
  serveur : aucune interférence entre plusieurs parties simultanées.
- Les statistiques de victoires / éliminations sont stockées dans
  `plugins/Push/stats.yml`, sauvegardées immédiatement à chaque action et
  à l'arrêt du serveur.
- Les moules de salles d'attente sont stockés dans
  `plugins/Push/lobbies/<arène>.yml` (palette compacte).
- Aucun plugin tiers n'est requis — Push est **autonome**.

---

## 📜 Licence

Ce projet est distribué sous licence **MIT**. Voir le fichier [LICENSE](LICENSE)
pour plus d'informations.

---

## 🤝 Crédits

Plugin original : équipe **Push** — https://github.com/herocraftlol/Push
Développé et maintenu pour la communauté **HeroCraft**.
