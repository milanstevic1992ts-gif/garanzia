# Garanzia

Archivio Android offline-first per scontrini e garanzie.

## Stato progetto
Fase 5 — confidence e anti-allucinazione integrati (controllo statico completato; build reale prevista in Fase 16).

## Principi
- Android nativo: Kotlin + Jetpack Compose + Material 3
- Database locale: Room / SQLite
- Dependency injection: Hilt
- Funzionamento offline-first
- Scontrino originale sempre conservato
- OCR e interpretazione eseguiti localmente
- Nessun backend nella prima versione
- I dati mancanti non vengono inventati: se un campo non è ricavabile dal testo OCR resta vuoto\n- Ogni campo interpretato conserva confidence, livello e riga OCR di evidenza\n- I valori sotto la soglia minima di affidabilità vengono scartati

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
5. ✅ Confidence e anti-allucinazione\n   - confidence per singolo campo combinando affidabilità OCR e forza della regola semantica\n   - livelli Alta / Media / Bassa\n   - evidenza OCR conservata per ogni valore estratto\n   - soglia minima: i valori troppo deboli non vengono mostrati\n   - subtotal, IVA e resto non possono essere promossi arbitrariamente a totale\n   - carta fedeltà non viene confusa con metodo di pagamento\n   - segnalazione di verifica consigliata quando mancano campi importanti o la confidence è bassa\n   - test unitari dedicati ai casi anti-allucinazione
6. Comprensione multi-prodotto
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
