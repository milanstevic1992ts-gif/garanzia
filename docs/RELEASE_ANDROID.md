# Release Android — Garanzia

## Identità applicazione

- Application ID: `com.milanstevic.garanzia`
- Release: `1.0.0`
- Version code: `5`

## Firma ufficiale

Le release installabili devono essere firmate con l'identità Android GE360 stabile.

Fingerprint SHA-256 pubblico del certificato atteso:

`34:B9:32:7E:45:00:C6:42:AF:CE:16:DB:23:04:C7:0D:C9:E6:38:E0:85:0E:F4:DA:CB:4A:36:BD:0B:E8:82:F4`

Il keystore, alias e le password NON devono essere committati nella repository.

## Pipeline

La CI esegue:

1. unit test;
2. banco prova OCR italiano;
3. Android lint;
4. `assembleRelease`;
5. pubblicazione dell'APK release non firmato come artefatto;
6. test strumentali Android su emulatore.

La firma finale viene applicata fuori dalla repository con il keystore GE360 privato.

## Verifica finale obbligatoria

Prima di distribuire un APK firmato verificare:

- package `com.milanstevic.garanzia`;
- versionName `1.0.0`;
- versionCode `5`;
- APK Signature Scheme v2 = true;
- APK Signature Scheme v3 = true;
- un solo signer;
- fingerprint SHA-256 uguale a quella GE360 attesa;
- `zipalign -c -P 16 -v 4` superato;
- SHA-256 del file finale salvato accanto all'APK.

Una release che non rispetta la fingerprint prevista non deve essere distribuita come aggiornamento GE360.
