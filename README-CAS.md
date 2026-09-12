# kiteconnect-cas

A fork of [zerodha/javakiteconnect](https://github.com/zerodha/javakiteconnect) 4.0.1 that
decodes the closing-auction packets the upstream ticker discards.

Upstream is MIT licensed; `LICENSE` and its copyright notice are unchanged. This is an
unofficial fork and is not endorsed by or affiliated with Zerodha.

## What it changes

The ticker dispatches on packet length with no `else` branch, so two packet sizes fell through
and were dropped without a tick or a warning:

| Size | Packet | Upstream | Here |
|---|---|---|---|
| 200 | Full + call auction | dropped silently | parsed, tagged `full_cas` |
| 36 | Index + indicative close | dropped silently | parsed, tagged `full_cas` |
| 184, 44, 32, 28, 8 | everything else | parsed | unchanged |

The auction block sits past the depth book, so the legacy fields decode identically either way:

```
200-byte full    reference limit price  @184..188   uint32, price
                 indicative close price @188..192   uint32, price
                 total imbalance qty    @192..200   int64, signed quantity

36-byte index    indicative close price @32..36     uint32, price
```

Those land on three new `Tick` fields: `referenceLimitPrice`, `indicativeClosePrice`,
`totalImbalanceQty`. A new mode constant `KiteTicker.modeFullCAS` (`"full_cas"`) both requests
the wider packet and marks the ticks that carried one.

Prices are unsigned on the wire and the imbalance is signed, so both get their own helpers
rather than reusing `convertToLong`, whose raw `getInt()` has no unsigned mask.

## Known upstream issues, deliberately NOT fixed here

Both are pre-existing and out of scope for this fork; they are recorded so they are not
mistaken for something introduced here.

1. **Missing unsigned mask.** `convertToLong` and the 4-byte branch of `convertToDouble` both
   use a signed `getInt()`. Every value routed through them — `volumeTradedToday`,
   `totalBuyQuantity`, `totalSellQuantity`, `oi`, `oiDayHigh`, `oiDayLow`,
   `lastTradedQuantity` and each depth `quantity` — goes negative above 2,147,483,647.
2. **No NSE-COM divisor.** The table is
   `(segment == NseCD) ? 1e7 : (segment == BseCD) ? 1e4 : 100`. Kite's own web client applies
   1e4 to NSE-COM as well as BSE-CD. The segment enum here has no `NseCOM` constant at all
   (slots 7 and 8 are `McxFO`/`McxSX`), so the branch cannot be added without the numeric
   segment id.

## Version

`com.zerodhatech.kiteconnect:kiteconnect:4.0.1-cas`

The groupId is upstream's because renaming it would churn every consumer; the `-cas` suffix is
what keeps the artifact distinct. A plain `4.0.1` would let `mvn -U` resolve Central's genuine
artifact over this one and take the auction parser away with **no build error**.

`dist/kiteconnect.jar` was removed. It was upstream's prebuilt fat jar and does not contain any
of this — a stale binary that looks official is the same trap the version suffix exists to
avoid. Consume the fork through Maven.

## Build and install locally

```bash
mvn clean install
```

Requires a JDK that still supports `--release 8` (JDK 22 works; JDK 26 does not).

Note that `<sourceDirectory>kiteconnect/src</sourceDirectory>` is set in the pom. Without it
Maven looks for `src/main/java`, finds nothing, and produces an **empty jar while exiting 0**.

## Publish to GitHub Packages

```bash
export GITHUB_ACTOR=GAURAVJAYSWAL
export GITHUB_TOKEN=<PAT with write:packages>
mvn deploy
```

## Consume it

GitHub Packages requires a token to **read** as well as write, even for a public repository.

`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

In the consuming pom:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/GAURAVJAYSWAL/kiteconnect-cas</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.zerodhatech.kiteconnect</groupId>
  <artifactId>kiteconnect</artifactId>
  <version>4.0.1-cas</version>
</dependency>
```

In GitHub Actions, `read:packages` on a PAT stored as a secret — the workflow's built-in
`GITHUB_TOKEN` is scoped to its own repository and cannot read another repository's packages.

## Tests

They live in `verify-consumer/`, a throwaway project that depends on the installed artifact,
so the library's own dependency list stays untouched. Same package as the ticker, so they drive
the real framing path through `parseBinary` rather than a reimplementation.

```bash
mvn clean install && (cd verify-consumer && mvn clean test)
```
