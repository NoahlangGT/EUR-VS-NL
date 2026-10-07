# CLAUDE.md

## Pflicht-Start jeder Session
1. `HT:kontext` laden — liefert Rang, Stil, System, App-Adressen, Stand.
2. Jüngste Befehle: `python3 /opt/heliotropica/abschnitt.py befehlsbuch neu 5`
3. Erst dann die aufgabenrelevanten Abschnitte laden.

## System
OPTIMaiZEx (optimaizex.com) — mandantenfähiges XaaS.
Besteht aus: einer öffentlichen High-Targeted-Landingpage + 8 integrierten Apps.
Mandant 1: Heliotropica (heliotropica.es). Mandant 2: Eurener (eurener.optimaizex.com).
Umsatzziel: 1.000.000 EUR bis 31.01.2027.

## Dokumente (nur bei App- oder Seitenbezug)
Abschnitt holen: `python3 /opt/heliotropica/abschnitt.py <dok> <Nummer oder Anker>`
Ganzer Abschnitt ohne Kürzung: `--alles`

| Dokument | Link | Kürzel |
|---|---|---|
| Index (alle Docs) | — | `abschnitt.py index` |
| Rahmen | https://heliotropica.es/dashboard/dox/Rahmen | rahmen |
| Grundbibel | https://heliotropica.es/dashboard/dox/Rahmen/0%20Grundbibel | grundbibel |
| Systemdefinition | https://heliotropica.es/dashboard/dox/Rahmen/3%20Systemdefinition | systemdefinition |
| Prompts | https://heliotropica.es/dashboard/dox/Rahmen | prompts |
| Befehlsbuch | https://heliotropica.es/dashboard/dox/Rahmen | befehlsbuch |
| Systembasis | https://heliotropica.es/dashboard/dox/Rahmen | systembasis |
| Formeln | https://heliotropica.es/dashboard/dox/Rahmen | formeln/\<datei\> |

## Rangfolge
Noahs jüngster Befehl > Rahmen > Befehlsbuch > Systemdefinition > Formelregister.
Bestand nie ersetzen, nur weiterentwickeln (Rahmen 8.9, B70).
Vor jedem Schreiben: `HT:sperre` setzen, danach freigeben.

## Serverarchitektur

| Ebene | Aufbau |
|---|---|
| Mother-Board | template.optimaizex.com. Web-Code genau einmal unter /var/www/mandanten/template: /ox/, alle App-Seiten, verbund, lib, Zeichen, Icons. |
| Mandanten | Eigene Inhalte je unter /var/www/mandanten/\<kennung\>: Marke, Logos, Bilder, DOX, Kataloge, Listen, Texte. Eigene Datenbank, eigener Kern, eigenes Postfach, eigener Schlüssel. |
| Synchronisation | nginx liefert zuerst aus Mandantenordner, sonst aus Mother-Board. Code einmal ändern — wirkt sofort bei allen. |
| Server-Code | /opt/optimaizex/kern. Alle Kerne laufen live daraus in eigenen Sandkästen. /opt/heliotropica ist nur noch Verweis für alte Pfade. |
| Single-Page-System | /ox/index.html ist das eine Dokument. Jede App ein Modul per import(), Router per pushState. Kein Neuladen beim Appwechsel. |
| Bestand | Alte App-Seiten (/dashboard/, /xannel/ …) laufen weiter aus dem Mother-Board bis /ox/ sie ablöst (Plan Schritt 9). |
