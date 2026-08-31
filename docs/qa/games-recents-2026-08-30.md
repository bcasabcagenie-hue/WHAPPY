# Jeux contre l’IA, billard et Récents — 30 août 2026

## Périmètre

Corrections locales à partir du code présent dans l’espace de travail. Aucun déploiement Hosting, Firebase, APK public, boutique ou Git distant. La version publique 2.1.28 ne contient donc pas encore ces changements. Les modifications antérieures présentes dans le dossier sont conservées.

## Android : corrections

- Échecs/dames : l’état « l’IA réfléchit » est dérivé du tour, au lieu d’un booléen sauvegardé susceptible de rester bloqué après annulation. Le calcul s’exécute hors du thread d’interface et reprend avec la difficulté choisie. Une partie terminée n’accepte plus de nouveaux coups.
- Moteur de stratégie : obligation de capture calculée une seule fois par recherche ; valorisation des véritables symboles de pièces ; correction de l’index aléatoire de difficulté facile.
- Saisie 3D : matrice caméra publiée sans mutation concurrente ; sélection du volume des pièces avant la case du plateau ; un appui rapide pièce/destination n’est plus traité comme une remise à zéro de la caméra.
- Échecs/dames : commandes dans une colonne latérale en paysage, sans retirer de hauteur au plateau ; règles et difficultés regroupées dans deux menus. La colonne défile indépendamment sur les petits écrans. En portrait, les contrôles restent hors de la surface du plateau.
- Ludo : animation des dés non restaurée en état bloqué après recréation ; annulation du lancer au redémarrage ou à la fermeture ; arrêt des tours IA à la victoire ; la sélection d’un pion reste au joueur humain.
- Billard : verrouillage du tir pendant la course de queue et la physique ; visée désactivée pendant le tour IA ; planification IA hors du thread principal ; angle de secours adapté aux proportions physiques de la table.
- Billard : avancée de la queue pendant 120 ms avant l’impulsion, puis son de contact ; sons suivants déclenchés par les véritables collisions, et non par un second impact artificiel programmé à l’avance.
- Récents : suppression du panneau intermédiaire et de la translation partielle des Messages. Un dépassement du seuil de 72 dp ouvre une seule page plein écran ; glisser en sens inverse la referme sans recréer la liste.

## iOS : changements correspondants

- Récents utilise une seule présentation plein écran dès le seuil de tirage, sans bandeau intermédiaire. Une sélection ferme la présentation avant la navigation.
- Billard : tâche IA annulée à la fermeture et au redémarrage ; isolation de la session SceneKit ; commandes verrouillées durant le tir ; arrêt des callbacks d’une ancienne scène ; aucun tir supplémentaire quand il n’existe plus de cible.
- Course de queue avant la force physique ; son joué au contact ; mêmes trois nouveaux fichiers audio qu’Android. Un petit pool de lecteurs permet les impacts rapprochés sans couper systématiquement le précédent.

## Sons et vérifications

Voir [la provenance audio](pool-audio-provenance.md) : enregistrement CC0 retraité, sans achat ni service externe. Les trois fichiers sont différents, non silencieux et sans échantillons saturés. Le test vérifie les mêmes octets sur les deux plateformes. L’ancien son de poche reste conservé.

- Android : `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `compileReleaseKotlin` réussis.
- 80 tests JVM réussis, dont coups IA légaux aux quatre difficultés, stabilisation physique après casse/réponse, sélection du volume des pièces, seuil Récents et validité des fichiers audio.
- iOS : compilation Debug simulateur arm64 réussie, avec les avertissements préexistants de cibles minimales dans certains Pods.
- Android, émulateur API 36.1 : trois tests instrumentés réussis, dernier passage en 54,208 s après la disposition latérale finale. Appuis physiques sur une pièce puis sa destination aux échecs et aux dames, sélection d’une autre difficulté, réponse IA et redémarrage ; traction réelle de la jauge de billard, au moins deux tirs et retour du contrôle ; deux ouvertures/fermetures de Récents avec contrôle des dimensions plein écran.
- Les premiers essais ont révélé un instantané d’émulateur noir, une lecture périmée du cache d’accessibilité et l’absence d’attente du premier rendu 3D. Le test final utilise un démarrage à froid, rafraîchit le cache et attend la caméra publiée ; il vérifie les coups par événements tactiles et non uniquement par appels au moteur de règles.
- Les activités de validation et données de test sont exclusivement dans `src/debug` / `src/androidTest` ; elles ne sont pas incluses dans l’application de production.

## Limites à conserver visibles

- Les parties en ligne, tournois et services backend ne sont ni modifiés ni validés ici.
- Les scénarios tactiles sont exécutés sur émulateur Android, pas sur le téléphone du demandeur. Le rendu logiciel de cet émulateur ne permet pas de certifier les performances GPU ni la restitution sonore d’un téléphone réel.
- Le Ludo bénéficie des correctifs d’état, mais une partie complète jusqu’à la victoire n’a pas été rejouée sur appareil dans cette passe.
- iOS : compilation et logique du billard contrôlées ; pas de validation tactile sur iPhone dans cette passe. Les autres jeux iOS ne disposent pas encore tous de la même logique de partie que leurs homologues Android. Leur parité IA reste un chantier distinct, et n’est pas déclarée terminée.
- Billard local Android : à la recréation de l’activité, le plateau et les scores repartent ensemble. La restauration intégrale d’une partie interrompue reste à développer ; on ne restaure plus des scores/tours appartenant à un plateau perdu.
- Ce travail améliore l’interaction et le rendu existants, sans prétendre remplacer le moteur par Unreal Engine ni atteindre un rendu photoréaliste.

## Fichiers concernés dans cette passe

- Android : `WhappyUi.kt`, `WapiGameRules.kt`, `WapiTabletop3DView.kt`, `WapiTabletopPicking.kt`, `WapiRecentPull.kt`, `WhappySounds.kt`, trois fichiers audio, activités debug et tests associés.
- iOS : `ContentView.swift`, `WapiGameSceneKit.swift`, `WhappyApp.swift`, trois fichiers audio et leurs entrées Xcode.
- Documentation : ce compte rendu et `pool-audio-provenance.md`.
