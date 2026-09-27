# Banco prova OCR italiano

Questa fase protegge il flusso OCR → interpretazione da regressioni prima della release.

## Cosa misura automaticamente

Il test JVM `ItalianOcrBenchmarkTest` alimenta `ReceiptInterpreter` con output OCR realistici italiani, completi di confidence per riga. Il corpus è intenzionalmente deterministico: serve a verificare che una modifica al parser non peggiori dati già leggibili.

Il banco prova copre:

- esercente;
- data e ora;
- totale e valuta;
- P.IVA;
- numero documento;
- metodo di pagamento;
- prodotti;
- quantità `x prezzo`;
- quantità in `PZ`;
- separatori italiani delle migliaia;
- scontrini multipagina;
- righe cassa/operatore/indirizzo;
- carta fedeltà;
- subtotale/IVA senza totale reale;
- confidence OCR troppo bassa;
- errori OCR comuni come `T0TALE`.

## Quality gate

La CI blocca il merge se il corpus scende sotto:

- esercente: 95%;
- data: 95%;
- totale: 95%;
- pagamento: 95%;
- precision prodotti: 95%;
- recall prodotti: 95%;
- decisione `Da verificare`: 100%.

Inoltre tutti i casi del corpus devono passare esattamente. Questo rende visibili anche regressioni che una media aggregata potrebbe nascondere.

## Report

Ogni esecuzione CI produce l'artefatto `italian-ocr-benchmark` con:

- `italian-ocr-benchmark.txt`;
- `italian-ocr-benchmark.json`.

Il report contiene metriche aggregate e risultato di ogni scenario.

## Cosa NON misura questo test

Questo benchmark non sostituisce la prova fotografica del modello PaddleOCR. Parte da righe OCR simulate e misura la qualità della trasformazione testo → dati strutturati.

Il riconoscimento immagine → testo dipende anche da:

- messa a fuoco;
- prospettiva;
- carta termica sbiadita;
- illuminazione;
- pieghe;
- risoluzione fotocamera;
- versione dei modelli PP-OCRv6.

Per un banco prova fotografico reale non vanno commessi scontrini di clienti o documenti con dati personali nel repository. Le foto vanno anonimizzate oppure mantenute in un dataset locale dedicato.

## Come aggiungere un caso

Aggiungere un nuovo `ItalianOcrBenchmarkCase` in:

`app/src/test/java/com/milanstevic/garanzia/benchmark/ItalianOcrBenchmarkCorpus.kt`

Ogni caso deve dichiarare:

1. righe riconosciute e confidence;
2. valori attesi;
3. prodotti attesi;
4. se la schermata deve richiedere verifica.

Un bug OCR/parsing corretto deve sempre diventare un nuovo caso di regressione.
