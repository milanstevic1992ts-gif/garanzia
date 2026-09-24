# Garanzia

Archivio Android offline-first per scontrini e garanzie.

## Stato progetto
Fase 8 — database locale Room integrato e collegato alla conferma (controllo statico completato; build reale prevista in Fase 16).

## Principi
- Android nativo: Kotlin + Jetpack Compose + Material 3
- Database locale: Room / SQLite
- Dependency injection: Hilt
- Funzionamento offline-first
- Scontrino originale sempre conservato
- OCR e interpretazione eseguiti localmente
- Nessun backend nella prima versione
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
   - barcode/EAN volutamente rimandati alla Fase 11
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
   - Room / SQLite con database `garanzia.db` versione 1
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
   - test strumentali con database Room in memoria, persistenza completa e `CASCADE`
   - nessuna UI archivio anticipata: resta alla Fase 9
9. Archivio garanzie
10. Scheda prodotto
11. Barcode / EAN
12. Motore garanzie e notifiche
13. Backup / ripristino locale
14. Sicurezza
15. Banco prova OCR italiano
16. Build release e APK firmata
