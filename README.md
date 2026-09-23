# Garanzia

Archivio Android offline-first per scontrini e garanzie.

## Stato progetto
Fase 3 — PaddleOCR PP-OCRv6 integrato (controllo statico completato; build reale prevista in Fase 16).

## Principi
- Android nativo: Kotlin + Jetpack Compose + Material 3
- Database locale: Room / SQLite
- Dependency injection: Hilt
- Funzionamento offline-first
- Scontrino originale sempre conservato
- OCR e intelligenza verranno introdotti nelle fasi successive
- Nessun backend nella prima versione

## Roadmap
1. ✅ Fondamenta Android
2. ✅ Scanner scontrino
3. ✅ PaddleOCR PP-OCRv6
   - SDK Android ufficiale PaddleOCR integrato come modulo `ppocr-sdk`
   - modelli PP-OCRv6 small scaricati al primo utilizzo e verificati SHA-256
   - OCR locale con testo, bounding box e confidence
   - lo scontrino originale viene conservato prima dell'OCR
4. Interpretazione intelligente dello scontrino
5. Confidence e anti-allucinazione
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
