# ☣ Atrocities

**Bioengenharia sombria para Minecraft 1.21.1 (NeoForge).**
Cace espécies, extraia material genético, sequencie embriões instáveis e incube chefes atrozes. **Nenhum chefe nasce naturalmente:** todos são criados pelos jogadores.

Feito para servidores PvP/Guerra com armamento pesado (~50 de dano por hit): chefes com milhares de HP, mitigação inteligente, ataques telegrafados e recompensas que justificam o risco.

## Download

Baixe o `.jar` mais recente em **[Releases](https://github.com/KoZuuYT/Atrocities/releases)**.

| Requisito | Versão |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265+ |
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | 4.9.3+ |
| Java | 21 |

O mod precisa estar **no servidor e em todos os clientes**. Quem entrar sem ele recebe uma mensagem com o link de download. Quem estiver com outra versão pode baixar e instalar a versão certa direto pela tela do jogo.

## Ciclo de gameplay

1. **Caça e captura:** rastreie espécies-base em biomas específicos, enfraqueça-as e use a Seringa de Extração ou a Cápsula de Estase.
2. **Biomassa e refino:** triture carne no Extrator de Biomassa para alimentar as máquinas e purifique amostras na Centrífuga Genética.
3. **Sequenciamento:** combine amostras para formar um Embrião Instável, que define arquétipo, traços e tier do chefe.
4. **Incubação:** mantenha a Incubadora Biológica alimentada com biomassa enquanto o embrião cresce no vidro. Embriões instáveis podem mutar ou abortar.
5. **Eclosão e combate:** liberte a aberração e enfrente-a.

## Roadmap

| Fase | Versão | Status |
|---|---|---|
| 1. Base, registros e VersionGate | 0.1.0 | ✅ |
| 2. Sistema genético (Data Components, seringa, cápsula) | 0.2.0 | ✅ |
| 3. Máquinas e incubação | 0.3.0 | ✅ |
| 4. Entidades, IA e animações GeckoLib | 0.4.0 | ⏳ |
| 5. Loot e balanceamento | 1.0.0 | ⏳ |

Veja o [CHANGELOG](CHANGELOG.md) para o histórico completo.

## Genética por datapack

Espécies e traços são JSON e podem ser alterados ou ampliados por datapack, inclusive com criaturas de outros mods:

- `data/<ns>/atrocities/species/<id>.json`: entidade, arquétipo (`chitinous`, `hemophage`, `coalesced`), cor, limites de vida para seringa e cápsula, pureza base e traços doados (peso e faixa de potência).
- `data/<ns>/atrocities/trait/<id>.json`: categoria, potência máxima, instabilidade por nível, cor e traços incompatíveis.

- `data/<ns>/data_maps/item/biomass_yield.json`: quanto cada item rende no Extrator de Biomassa (100 = 1 Biomassa).

Exemplo: [`species/ravager.json`](src/main/resources/data/atrocities/atrocities/species/ravager.json).

## Configuração do servidor

`config/atrocities-common.toml`:

| Opção | Padrão | Descrição |
|---|---|---|
| `attributes.maxHealthCap` | 1000000 | Teto do atributo de vida máxima (vanilla = 1024) |
| `bossCombat.softCapThreshold` | 30 | Dano por hit a partir do qual o soft cap atua |
| `bossCombat.softCapFactor` | 0.35 | Fração do excedente que passa pelo soft cap |
| `versionGate.kickClientsWithoutMod` | true | Desconecta quem não tem o mod, mostrando o link de download |
| `versionGate.requireExactVersion` | false | Exige a mesma versão do servidor |
| `versionGate.githubRepo` | KoZuuYT/Atrocities | Repositório usado nos links de download |

## Compilar

```powershell
powershell -ExecutionPolicy Bypass -File .\build-and-export.ps1   # jar em libs/
powershell -ExecutionPolicy Bypass -File .\release.ps1            # publica a Release no GitHub
```

## Licença

All Rights Reserved © kozzy.
