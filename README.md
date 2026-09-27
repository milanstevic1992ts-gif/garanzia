# Garanzia

Archivio Android offline-first per scontrini e garanzie.

## Stato progetto
Fase 12 — backup e ripristino locale integrati (ZIP versionato, checksum, staging e ripristino atomico; build release prevista in Fase 15).

## Principi
- Android nativo: Kotlin + Jetpack Compose + Material 3
- Database locale: Room / SQLite
- Dependency injection: Hilt
- Funzionamento offline-first
- Scontrino originale sempre conservato
- OCR e interpretazione eseguiti localmente
- Nessun backend nella prima versione
- Barcode / EAN escluso dalla roadmap: non necessario per il flusso dell'app
- I dati mancanti non vengono inventati: se un campo non è ricavabile dal testo OCR resta vuoto
- Ogni campo interpretato conserva confidence, livello e riga OCR di evidenza
- I valori sotto la soglia minima di affidabilità vengono scartati

## Roadmap
1. ✅ Fondamenta Android
2. ✅ Scanner scontrino
3. ✅ PaddleOCR PP-OCRv6
   - SDK Android ufficiale PaddleOCR integrato come modulo `ppocr-sdk`
   - modelli PP-OCRv6 small scaricati al primo utilizzo e verificati SHA-256
   - OCR locale con testo, bounding box e confidence
   - lo scontrino originale viene conservato prima dell'OCR
4. ✅ Interpretazione intelligente dello scontrino
   - parser locale separato dal motore OCR
   - riconoscimento negozio/esercente
   - data e ora di acquisto
   - totale e valuta quando presenti
   - partita IVA
   - numero documento/scontrino
   - metodo di pagamento
   - nessuna suddivisione dei prodotti in questa fase
   - test unitari sui casi italiani principali
5. ✅ Confidence e anti-allucinazione
   - confidence per singolo campo combinando affidabilità OCR e forza della regola semantica
   - livelli Alta / Media / Bassa
   - evidenza OCR conservata per ogni valore estratto
   - soglia minima: i valori troppo deboli non vengono mostrati
   - subtotal, IVA e resto non possono essere promossi arbitrariamente a totale
   - carta fedeltà non viene confusa con metodo di pagamento
   - segnalazione di verifica consigliata quando mancano campi importanti o la confidence è bassa
   - test unitari dedicati ai casi anti-allucinazione
6. ✅ Comprensione multi-prodotto
   - più prodotti estratti dallo stesso scontrino
   - supporto prodotto + prezzo sulla stessa riga
   - supporto nome prodotto su una riga e quantità/prezzo sulla riga successiva
   - quantità esplicite tipo `2 x 4,50` e `2 PZ`
   - prezzo unitario e importo riga quando realmente presenti
   - confidence ed evidenza OCR per ogni prodotto
   - esclusione di totale, subtotale, IVA, resto, pagamento e metadata
   - protezioni contro falsi positivi come `carta abrasiva`
   - prefissi come `ARTICOLO` / `PRODOTTO` puliti senza perdere il nome reale
   - test unitari multi-prodotto e test di integrazione nel risultato scontrino
7. ✅ Conferma intelligente
   - schermata dedicata dopo OCR e interpretazione
   - campi con confidence bassa o mancanti evidenziati come `Da verificare`
   - i campi affidabili restano precompilati senza obbligare a ricontrollare tutto
   - modifica manuale di negozio, data, ora, totale, valuta, P.IVA, documento e pagamento
   - modifica manuale di nome prodotto, quantità, prezzo unitario e importo riga
   - possibilità di rimuovere falsi prodotti OCR
   - possibilità di aggiungere prodotti mancanti manualmente
   - almeno un prodotto è obbligatorio prima della conferma
   - validazione stretta della data e degli importi prima della conferma
   - gli indicatori `Da verificare` si risolvono dopo una correzione manuale
   - conferma mantenuta solo nella sessione: nessun salvataggio database anticipato
   - test unitari del modello di conferma e delle validazioni
8. ✅ Database locale completo
   - Room / SQLite con database `garanzia.db` versione 3
   - tabella scontrini con dati confermati, data canonica e testo OCR grezzo
   - tabella prodotti collegata allo scontrino con ordine stabile
   - tabella pagine originali con URI dei file conservati
   - foreign key con `CASCADE` tra scontrino, prodotti e pagine
   - indici su data, esercente, data conferma e nomi prodotto
   - vincolo univoco su posizione prodotto e indice pagina per scontrino
   - salvataggio atomico di scontrino + prodotti + pagine in una transazione
   - Repository Room iniettato con Hilt
   - il tasto `Conferma scontrino` salva realmente nel database locale
   - stato di salvataggio ed eventuale errore mostrati nella schermata di conferma
   - Home collegata a `Flow` Room con contatore persistente `Scontrini salvati`
   - lettura singolo scontrino, osservazione elenco e cancellazione predisposte per la Fase 9
   - schema export Room configurato per le future migrazioni
   - schema Room JSON versionato nella repository
   - migrazioni esplicite `1 → 2 → 3` senza fallback distruttivo
   - migrazioni verificate su emulatore Android preservando scontrini e prodotti preesistenti
   - indice FTS4 Unicode per ricerca scalabile su archivio
   - test strumentali con database Room in memoria, persistenza completa e `CASCADE`
   - nessuna UI archivio anticipata: resta alla Fase 9
9. ✅ Archivio garanzie
   - accesso diretto dalla Home con pulsante `Apri archivio`
   - elenco scontrini alimentato in tempo reale dal `Flow` Room già esistente
   - card archivio con negozio, data, totale, numero prodotti e pagine
   - anteprima dei primi prodotti direttamente nella card
   - ricerca unica su negozio, nome prodotto, numero documento, P.IVA e testo OCR
   - ricerca anche tramite data nel formato italiano `gg/mm/aaaa`
   - filtro intervallo date `Dal` / `Al` con validazione strict
   - intervalli invertiti o date impossibili vengono segnalati e non producono risultati
   - ordinamento `Più recenti` / `Più vecchi`
   - filtri mantenuti quando si apre un dettaglio e si torna all'archivio
   - dettaglio scontrino con dati acquisto e lista prodotti ordinata
   - lettore PDF integrato basato su `PdfRenderer`, senza app esterne
   - PDF generato al volo dalle immagini originali solo quando viene aperto
   - rendering di una pagina alla volta per ridurre memoria e rischio crash
   - gestione errore e `Riprova` se il PDF non è leggibile
   - visualizzazione del testo OCR originale conservato nel database
   - schermata vuota e stato `nessun risultato` gestiti
   - test unitari su ricerca, prodotto, documento, data, intervalli e ordinamento
   - nessuna `scheda prodotto` dedicata anticipata: resta alla Fase 10
10. ✅ Scheda prodotto
   - accesso dalla lista prodotti del dettaglio scontrino
   - schermata dedicata per il singolo prodotto
   - nome, quantità, prezzo unitario e importo riga
   - negozio, data, ora, documento, P.IVA e metodo di pagamento collegati
   - confidence del riconoscimento prodotto quando disponibile
   - collegamento diretto al PDF originale dello scontrino
   - il lettore PDF ritorna alla scheda prodotto quando aperto da lì
   - accesso alla modifica dello scontrino mantenendo il prodotto legato al documento originale
   - controllo di ownership: un prodotto non può essere mostrato sotto uno scontrino diverso
   - test unitari dedicati al mapping prodotto/scontrino
11. ✅ Motore garanzie e notifiche
   - durata garanzia configurabile per singolo prodotto in mesi
   - scadenza calcolata automaticamente dalla data di acquisto
   - stato `Attiva`, `In scadenza` o `Scaduta`
   - giorni rimanenti mostrati nella scheda prodotto
   - preavviso configurabile da 1 a 365 giorni
   - notifiche Android opzionali per singolo prodotto
   - un avviso quando la garanzia entra nella finestra di preavviso
   - un secondo avviso quando la garanzia risulta scaduta
   - protezione contro notifiche duplicate tramite chiave persistente
   - controllo periodico con WorkManager ogni 24 ore
   - controllo immediato dopo il salvataggio della garanzia
   - permesso notifiche richiesto su Android 13+ solo quando necessario
   - dati garanzia preservati quando lo scontrino viene modificato
   - migrazione Room `2 → 3` non distruttiva
   - test unitari del calcolo scadenza e test strumentale della migrazione
12. ✅ Backup / ripristino locale
   - esportazione manuale tramite selettore file Android in un unico ZIP
   - manifest JSON versionato con versione formato e schema database
   - backup completo di scontrini, prodotti, OCR, garanzie e impostazioni di notifica
   - immagini originali incluse nel pacchetto senza rigenerarle
   - SHA-256 e dimensione registrati per ogni pagina originale
   - marcatore finale obbligatorio per riconoscere pacchetti incompleti
   - scrittura prima su file temporaneo e solo dopo sulla destinazione scelta
   - anteprima del backup prima del ripristino con conteggio scontrini, prodotti e pagine
   - conferma esplicita prima di sostituire l'archivio locale
   - estrazione in staging prima di modificare database o originali
   - protezione da path traversal, file duplicati e ZIP anomali
   - limiti di sicurezza su numero scontrini, pagine, dimensione singola e dimensione totale
   - verifica completa dei checksum prima di toccare l'archivio corrente
   - sostituzione dell'archivio Room in una singola transazione
   - conservazione degli ID di prodotti e pagine per mantenere intatte le garanzie
   - rollback: se il database non accetta il ripristino, i nuovi originali vengono rimossi
   - pulizia degli originali precedenti solo dopo il successo della transazione
   - invalidazione cache PDF dopo il ripristino
   - riavvio controllo notifiche garanzia e sincronizzazione esterna dopo il ripristino
   - test strumentali su round-trip manifest, ripristino reale Room e rifiuto di ZIP manomesso
   - le autorizzazioni alle cartelle Telefono/Drive restano configurazioni del dispositivo e non vengono trasferite nel backup
13. Sicurezza
14. Banco prova OCR italiano
15. Build release e APK firmata


## Restyling UI

- design system Garanzia con palette light/dark dedicata
- superfici e card arrotondate con gerarchia visiva coerente
- Home ridisegnata con metriche, azione principale e stato backup
- Archivio ridisegnato con filtri raccolti in una card e card scontrino più leggibili
- dettaglio scontrino organizzato in riepilogo, prodotti, PDF e OCR
- conferma scontrino semplificata con evidenza chiara degli elementi da verificare
- risultato OCR organizzato per dati riconosciuti, prodotti e testo originale
- archiviazione telefono/Drive con stato visivo e sincronizzazione più chiara
- lettore PDF integrato ridisegnato
- revisione scansione e fotocamera fallback rifinite graficamente


### Rifinitura premium

- bottom navigation fissa con Home / Archivio / Backup
- transizioni crossfade brevi tra schermate
- card e badge con micro-animazioni di ridimensionamento
- skeleton loading pulsante per OCR e PDF
- messaggi backup/sincronizzazione con comparsa e scomparsa animate
- Home semplificata con una sola azione primaria e navigazione persistente
- icone Material dedicate nella navigazione
