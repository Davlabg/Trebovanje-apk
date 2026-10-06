# Trebovanje – Android aplikacija za poručivanje robe

Aplikacija učitava nedeljni izveštaj iz Excel-a (.xlsx) i za svaki artikal računa koliko treba poručiti za nedelju dana.

- **Naziv artikla** – kolona **B** (`item`)
- **Nedeljna prodaja** – kolona **S** (`usage`)
- **Trenutno stanje** – unosite u aplikaciji
- **Poručiti** = nedeljna prodaja − stanje (zaokruženo naviše, najmanje 0)

Ako red zaglavlja sadrži nazive `item` i `usage`, aplikacija koristi te kolone i kad su pomerene.

## Upotreba

1. Meni (⋮) → **Uvezi Excel** i izaberite izveštaj (.xlsx). Stari `.xls` format treba prvo sačuvati kao `.xlsx`.
2. Dodirnite artikal, unesite trenutno stanje. Dugme **Sledeći** prelazi na naredni artikal.
3. **Prikaži samo artikle za poručivanje** – filter liste.
4. Meni → **Podeli porudžbinu** (Viber, WhatsApp, e-mail…) ili **Sačuvaj porudžbinu (Excel)**.

Uneta stanja se čuvaju na telefonu. Pri uvozu novog izveštaja stanje se zadržava za artikle sa istim nazivom; **Obriši uneto stanje** kreće ispočetka.

## Preuzimanje APK-a

Svaki push na GitHub pokreće GitHub Actions koji pravi APK i objavljuje ga u sekciji **Releases** (`Trebovanje.apk`).
Na telefonu dozvolite instalaciju iz nepoznatih izvora.

## Lokalni build

```
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`
