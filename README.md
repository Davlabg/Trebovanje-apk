# Nedeljko – Android aplikacija za nedeljno poručivanje robe

*Svake nedelje zna šta fali.* (ranije: Trebovanje)

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

## Količina u TP (transportno pakovanje)

- Tabela **Količina u TP** (koliko kg/kom iz Usage izveštaja ima u jednom TP) je ugrađena u aplikaciju (`app/src/main/assets/kolicina_u_tp.tsv`).
- Artikli se povezuju sa Usage listom po **šifri artikla**, a zatim po **nazivu** (isti naziv, ili jednoznačan početak naziva).
- Novu tabelu možete uvesti preko menija → **Uvezi količine u TP (Excel)** (kolone *Ime proizvoda*, *Kolicina u TP*, *Sifra artikla*);
  meni → **Vrati ugrađenu tabelu TP** vraća onu iz aplikacije.
- U prozoru artikla polje **Količina u 1 TP** može se ručno promeniti (pamti se i pri novom uvozu).
- **Popis u TP**: dugmad *TP / kg* biraju da li se stanje upisuje u broju TP ili u jedinici iz izveštaja; aplikacija sama preračunava
  (npr. 3 TP × 15 kg = 45 kg). Izbor se pamti.
- **Predikcija**: Poručiti = (potrebno − stanje) / TP, zaokruženo naviše na pun TP, uz prikaz potrebne količine iz Usage-a
  i količine koja stiže sa punim TP (npr. *2 TP = 30 kg, treba 22 kg*). U listi: veliki broj je TP, ispod je količina.
- **Mini kalkulator** u polju stanja: + − × ÷ ( ) = ⌫, npr. `3*15+4` ili `(12+8)/2`; rezultat se vidi odmah ispod polja.
- Excel porudžbina ima i kolone *Kol. u TP*, *Stanje (TP)*, *Poručiti (TP)*, *Poručiti po TP (kol.)*.

## Slanje porudžbine mejlom

- Meni → **Podešavanja mejla**: upišite adrese na koje se šalje (jedna po redu), Gmail nalog sa koga se šalje
  (podrazumevano `utrosak.cw.restoran@gmail.com`, isto kao u aplikaciji Utrošak) i **lozinku za aplikaciju** od 16 slova
  (myaccount.google.com/apppasswords). Lozinka se čuva samo na telefonu. „Probni mejl” proverava da li prijava radi.
- Dugme **Pošalji** (donja traka, ili meni → *Pošalji porudžbinu mejlom*) odmah šalje Excel porudžbinu u prilogu i
  spisak u tekstu na sve zadate adrese (smtp.gmail.com, port 465).
- Bez upisane lozinke, dugme otvara Gmail sa upisanim primaocima, naslovom, tekstom i prilogom – ostaje samo „Pošalji”.

## Upotreba

1. Meni (⋮) → **Uvezi Excel** i izaberite izveštaj (.xlsx). Stari `.xls` format treba prvo sačuvati kao `.xlsx`.
2. Dodirnite artikal, unesite trenutno stanje. Dugme **Sledeći** prelazi na naredni artikal.
3. **Korekcija u procentima** (po artiklu): u prozoru za unos stanja upišite npr. `20` ili `-15` (ili dugmad −10% / +10%).
   Potrebno = nedeljna prodaja × (1 + korekcija/100), pa Poručiti = Potrebno − stanje. Korekcija se pamti i za sledeće nedelje;
   meni → **Obriši korekcije (%)** ih briše.
4. **Safety stock** (po artiklu, u jedinici iz izveštaja): minimalna količina koja uvek treba da ostane.
   Potrebno = nedeljna prodaja × (1 + korekcija/100) + safety stock. Pamti se i pri uvozu novih izveštaja, dok se ručno ne promeni.
5. **Prikaži samo artikle za poručivanje** – filter liste.
6. Meni → **Podeli porudžbinu** (Viber, WhatsApp, e-mail…) ili **Sačuvaj porudžbinu (Excel)**.

Uneta stanja se čuvaju na telefonu. Pri uvozu novog izveštaja stanje se zadržava za artikle sa istom šifrom; **Obriši uneto stanje** kreće ispočetka.

## Preuzimanje APK-a

Svaki push na GitHub pokreće GitHub Actions koji pravi APK i objavljuje ga u sekciji **Releases** (`Nedeljko.apk`).
Na telefonu dozvolite instalaciju iz nepoznatih izvora.

## Lokalni build

```
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`
