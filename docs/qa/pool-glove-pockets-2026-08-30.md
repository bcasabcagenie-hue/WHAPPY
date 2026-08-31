# Billard — gant et poches encastrées

## Demande

Référence : les trois captures de billard jointes le 30 août 2026. Reprendre la lisibilité des six ouvertures et la main gantée, sans copier les portraits, marques, monnaie ni assets des captures. Modifications locales Android/iOS, sans publication.

## Modifications

- Gant ivoire mat : doigts articulés, pouce, renforts, trois coutures dorsales et manchette marine côtelée. Décalé latéralement pour que la blanche reste visible. Il suit toujours la position réelle de la bille ; aucune animation autonome ne valide le placement.
- Ouvertures noires encastrées, bord fin en cuir sombre ; suppression de l’anneau chromé saillant. Les centres des six poches et leurs capteurs restent inchangés.
- Android : découpe et lèvre définies avec des constantes communes, shader de tissu distinct du marbre, coins et bandes biseautés par un maillage dédié.
- iOS : découpe du plateau à l’extérieur du cuir pour éviter la superposition des parois en bois et de l’intérieur noir. Le collider des bandes reste identique ; leur géométrie visible est séparée.
- Tapis bleu plus sobre, bois acajou moins jaune, éclairage iOS réduit uniquement pour le billard et suppression du bloom qui surexposait les surfaces blanches. Texture de tissu iOS procédurale déterministe, RGBA et mipmaps.
- Le point d’entrée iOS `--wapi-pool-glove-test` n’existe que sous `DEBUG` et sert à l’inspection du gant ; il ne modifie pas le parcours de production.

## Vérifications

- Android Debug et Kotlin Release compilés ; 84 tests unitaires réussis, zéro échec.
- Tests de géométrie : six centres alignés avec la physique, lèvre ne recouvrant pas l’ouverture, faces du nouveau maillage dans le bon sens, cadrage sur plusieurs ratios.
- iOS : compilation simulateur arm64 réussie.
- Captures inspectées dans les deux simulateurs ; le premier contrôle a conduit au décalage du gant et à la correction des parois de poches iOS.
- Les trois tests tactiles de placement avec confirmation, visée sans tir involontaire et tir suivi du tour IA ont réussi sur la compilation finale (28 secondes).
- Captures finales : `/tmp/wapi-pool-glove-android-final.png` et `/tmp/wapi-pool-glove-ios-final.png`. Ce sont des captures des applications exécutées, pas des maquettes générées.

## Limites

Pas de nouveau moteur Unreal/Vulkan, aucune modification des règles ou des résultats serveur, ni activation de championnats ou d’argent réel. Le rendu reste procédural ; il n’est pas présenté comme photoréaliste. La validation sur téléphones physiques reste à effectuer. Le problème de sélection aux dames signalé dans le lot précédent n’est pas couvert par ce correctif visuel du billard.

Fichiers : `WapiTabletop3DView.kt`, `WapiPoolPresentation.kt`, `WapiPoolPresentationTest.kt`, `WapiGameSceneKit.swift`, `WapiPoolPlayers.swift`.
