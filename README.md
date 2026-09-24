# Garanzia

Archivio Android offline-first per scontrini e garanzie.

## Stato progetto
Fase 6 — comprensione multi-prodotto integrata (controllo statico completato; build reale prevista in Fase 16).

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
7. Conferma intelligente
8. Database locale completo
9. Archivio garanzie
10. Scheda prodotto
11. Barcode / EAN
12. Motore garanzie e notifiche
13. Backup / ripristino locale
14. Sicurezza
15. Banco prova OCR italiano
16. Build release e APK firmata
