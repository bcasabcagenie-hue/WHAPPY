# Billard — sources des nouveaux impacts

Source : [Quick Pool Break, MarkBuckawicki, Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Quick_Pool_Break.webm), enregistrement du 27 février 2014. Licence indiquée par l’auteur : CC0 1.0, domaine public, réutilisation commerciale et modification autorisées.

Fichier original téléchargé : `https://upload.wikimedia.org/wikipedia/commons/3/35/Quick_Pool_Break.webm`.

Extraits mono PCM 16 bits, 44 100 Hz, identiques dans Android et iOS :

- `wapi_pool_cue.wav` : 2,550–2,670 s, coupe des graves sous 180 Hz, filtre 9,5 kHz, gain 0,75 et fondus courts.
- `wapi_pool_collision.wav` : 3,155–3,275 s, filtres 180 Hz / 11 kHz, gain 0,80 et fondus courts.
- `wapi_pool_cushion.wav` : 3,800–3,990 s, impact filtré 80 Hz / 2,3 kHz, gain 0,65 et fondus, pour une bande plus mate.

Ce sont des extraits retraités d’une partie réelle, et non trois captations isolées en studio. L’échantillon de poche antérieur reste conservé. Aucun extrait musical ou dialogue n’est ajouté. Les sons sont déclenchés par la frappe et les contacts physiques ; leur intensité suit l’impact.

L’extraction utilise `atrim`, puis `asetpts=PTS-STARTPTS` avant les fondus : appliquer un fondu à 0,100 s sur le timestamp original de la vidéo rendait le résultat silencieux. Contrôle final avec `volumedetect` : cue maximum −2,0 dB / moyenne −22,4 dB ; collision −1,4 / −26,1 dB ; bande −5,6 / −21,5 dB. Le test JVM `WapiPoolAudioAssetTest` vérifie le PCM non silencieux, la durée, l’absence de saturation, deux impacts distincts et les mêmes octets sur iOS.
