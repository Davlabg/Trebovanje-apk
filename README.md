# Trebovanje – Android aplikacija za poručivanje robe

Aplikacija učitava nedeljni izveštaj iz Excel-a (.xlsx) i za svaki artikal računa koliko treba poručiti za nedelju dana.

- **Šifra artikla** – kolona **A** (`Item Code`)
- **Naziv artikla** – kolona **B** (`Item`)
- **Jedinica mere** – kolona **D** (`Manage Unit`)
- **Nedeljna prodaja** – kolona **S** (`Usage`); izveštaj potrošnju beleži kao negativan broj, pa aplikacija okreće znak
- **Trenutno stanje** – unosite u aplikaciji
- **Poručiti** = nedeljna prodaja − stanje (zaokruženo naviše, najmanje 0)

Ako red zaglavlja sadrži nazive `item` i `usage`, aplikacija koristi te kolone i kad su pomerene.

## Stanje eksternog magacina

Preko istog dugmeta (meni → **Uvezi Excel** ili **Uvezi stanje magacina**) može se uvesti i izveštaj
`ArticlesOnStock` – aplikacija sama prepoznaje koji je fajl.

- Koriste se kolone **NazivArtikla**, **InterniKodArtikla**, **NaStanju** (ili **Količina**), **JedinicaMere** i **Status**.
- Stanje u magacinu se prikazuje u jedinici iz magacina (komad), a nedeljna prodaja u jedinici iz Usage izveštaja (npr. kg) – bez pretvaranja.
- Sabiraju se samo redovi sa statusom **Regularno** (svi lotovi i rokovi); artikli sa statusom **Carina** (i Blokirano) se ne stavljaju na stanje.
- Šifre magacina se ne poklapaju sa šiframa iz Usage izveštaja, pa se artikli povezuju po nazivu: sigurni parovi automatski,
  ostali jednom ručno (dodirnite artikal → **Poveži sa magacinom**, najsličniji nazivi su na vrhu). Veze se pamte.
- Jedan artikal se može povezati sa **više šifara** iz magacina (označite sve); stanja označenih šifara se sabiraju.
- Crveno (⚠ nema dovoljno) se označava kad magacin nema ništa, ili kad su jedinice iste a u magacinu ima manje nego što treba poručiti.

## Upotreba

1. Meni (⋮) → **Uvezi Excel** i izaberite izveštaj (.xlsx). Stari `.xls` format treba prvo sačuvati kao `.xlsx`.
2. Dodirnite artikal, unesite trenutno stanje. Dugme **Sledeći** prelazi na naredni artikal.
3. **Prikaži samo artikle za poručivanje** – filter liste.
4. Meni → **Podeli porudžbinu** (Viber, WhatsApp, e-mail…) ili **Sačuvaj porudžbinu (Excel)**.

Uneta stanja se čuvaju na telefonu. Pri uvozu novog izveštaja stanje se zadržava za artikle sa istom šifrom; **Obriši uneto stanje** kreće ispočetka.

## Preuzimanje APK-a

Svaki push na GitHub pokreće GitHub Actions koji pravi APK i objavljuje ga u sekciji **Releases** (`Trebovanje.apk`).
Na telefonu dozvolite instalaciju iz nepoznatih izvora.

## Lokalni build

```
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`
