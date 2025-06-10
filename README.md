# TreeMaster

![Minecraft Plugin Icon](https://img.shields.io/badge/Minecraft-Plugin-brightgreen)
![Spigot Compatible](https://img.shields.io/badge/Spigot-Compatible-orange)
![Version](https://img.shields.io/badge/Version-1.0.0-blue)
![License](https://img.shields.io/github/license/Gabry-Dev/TreeMaster?color=red)

Un plugin essenziale per Minecraft che rivoluziona il modo in cui i giocatori interagiscono con gli alberi! Con TreeMaster, abbattere interi alberi e spogliare i tronchi diventa un'esperienza fluida e automatizzata, risparmiando tempo e fatica.

---

## ✨ Caratteristiche

* **Abbattimento Alberi Completo**: Rompi il blocco alla base di un albero e l'intero tronco (e sezioni 2x2 per alberi grandi) verrà automaticamente rimosso, rilasciando tutti i blocchi di legno.
* **Modalità Stripping Automatica**: Con un'ascia in mano, fai clic destro su un blocco di legno per spogliare istantaneamente tutti i tronchi collegati nell'albero.
* **Durabilità degli Strumenti Integrata**: Le tue asce subiscono danni in base ai blocchi rotti o strippati, proprio come in Minecraft vanilla, rispettando gli incantamenti di Unbreaking.
* **Compatibilità Totale con Vanilla**: Funziona con tutti i tipi di tronchi di Minecraft (anche Nether Stems e Bamboo Block) e le loro varianti strippate.
* **Ignora Blocchi Piazzati dal Giocatore**: I blocchi di legno piazzati dai giocatori non vengono abbattuti o strippati automaticamente, evitando modifiche indesiderate a costruzioni.
* **Cooldown Configurable**: Previene l'abuso delle funzioni con un breve cooldown personalizzabile.
* **Preferenze per Giocatore**: Ogni giocatore può abilitare o disabilitare le funzioni di abbattimento e stripping a proprio piacimento tramite comandi.
* **Persistenza Dati**: Le preferenze dei giocatori e i blocchi piazzati vengono salvati automaticamente e ricaricati al riavvio del server.

---

## 🚀 Installazione

1.  **Scarica** l'ultima versione del plugin TreeMaster da [qui](https://www.spigotmc.org/resources/treemaster.125930/).
2.  **Sposta** il file `.jar` scaricato nella cartella `plugins` del tuo server Spigot/Paper.
3.  **Riavvia** o **ricarica** il tuo server Minecraft.
4.  Il plugin creerà automaticamente una cartella `TreeMaster` con il file `players.yml` al suo interno, per salvare le preferenze dei giocatori.

---

## 🎮 Comandi

Il comando principale è `/treemaster`.

* `/treemaster function <break|strip> <enable|disable>`
    * **`<break|strip>`**: Specifica la funzione da modificare (`break` per l'abbattimento alberi, `strip` per lo stripping).
    * **`<enable|disable>`**: Abilita o disabilita la funzione per il tuo giocatore.

**Esempi:**
* `/treemaster function break enable` - Abilita l'abbattimento automatico degli alberi.
* `/treemaster function strip disable` - Disabilita la modalità stripping.

---

## 🔒 Permessi

* `treemaster.use`: Permette ai giocatori di usare il comando `/treemaster` per gestire le proprie preferenze.
    * *Questo è l'unico permesso richiesto per le funzionalità utente.*

---

## 🛠️ Contribuisci

Se desideri contribuire al progetto, sei il benvenuto! Puoi:

1.  Fare un **Fork** del repository.
2.  Creare un **Branch** per la tua nuova funzionalità (`git checkout -b feature/AmazingFeature`).
3.  **Committa** le tue modifiche (`git commit -m 'Add some AmazingFeature'`).
4.  Effettua il **Push** al branch (`git push origin feature/AmazingFeature`).
5.  Apri una **Pull Request**.

---

## 📄 Licenza

Questo progetto è rilasciato sotto la licenza MIT. Vedi il file `LICENSE` per maggiori dettagli.

---

## 📧 Contatti

Per qualsiasi domanda o suggerimento, puoi aprire una issue su GitHub.

---

**Spero che TreeMaster renda la tua esperienza con Minecraft più efficiente e divertente!**
