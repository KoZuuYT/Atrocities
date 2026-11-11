# ☣ Atrocities

**Mutantes de bioengenharia para Minecraft 1.21.1 (NeoForge).**
Injete Soro Mutagênico em zumbis, esqueletos, creepers, aranhas e phantoms e veja cada um virar um mutante gigante, cheio de golpes animados. No fim de jogo, incube embriões e desperte as Aberrações Primordiais.

Feito para servidores PvP/Guerra com armamento pesado (~50 de dano por hit): mutantes e chefes com centenas a milhares de HP, ataques telegrafados e relíquias que valem o risco.

## Download

Baixe o `.jar` mais recente em **[Releases](https://github.com/KoZuuYT/Atrocities/releases)**.

| Requisito | Versão |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265+ |
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | 4.9.3+ |
| Java | 21 |

O mod precisa estar **no servidor e em todos os clientes**. Quem entrar sem ele recebe uma mensagem com o link de download. Quem estiver com outra versão pode baixar e instalar a versão certa direto pela tela do jogo.

## Mutação

1. **Colete uma amostra** de qualquer criatura com a Seringa de Extração.
2. **Prepare o Soro Mutagênico:** Frasco de Amostra + Amostra Genética + Biomassa + olho de aranha fermentado + pó de pedra luminosa (rende 2).
3. **Injete três doses** num mob (ou arremesse o frasco). Cada dose deixa o mob maior e mais forte. Na terceira, ele entra em metamorfose e explode no mutante.
4. **Soro Volátil** na última dose gera um mutante **Alfa**, mas cada dose volátil tem 15% de chance de estourar o mob.

| Mob | Mutante | Vida | Relíquia |
|---|---|---|---|
| Zumbi, afogado, zumbi do deserto, aldeão zumbi | Titã Zumbi | 700 | Punho do Titã |
| Esqueleto, errante, pantanoso | Esqueleto Balista | 560 | Arco Balista |
| Creeper | Creeper Detonador | 520 | Núcleo Detonador |
| Aranha, aranha-das-cavernas | Aranha Tecelã | 600 | Glândula Tecelã |
| Phantom | Arraia-Fantasma | 480 | Membrana Etérea |

Todo mutante entra em **Fúria** na metade da vida. Alfas têm +50% de vida e +30% de dano.
Operadores podem usar `/atrocities mutate <alvos> [volátil]`.

## Imbuição genética

O **Terminal de Imbuição** grava traços de uma Amostra Genética numa arma ou peça de armadura.
Receita: fragmento de ametista, diamante, Frasco de Amostra, mesa de ferraria, lingote de ferro e comparador.

1. Coloque a arma ou armadura, a Amostra Genética e um catalisador (Soro Mutagênico ou Soro Volátil). O terminal consome biomassa.
2. Cada item tem **3 tanques genéticos**. O nível inicial de cada gene depende da potência do traço e da pureza da amostra.
3. **Soro Volátil** dá +1 nível, mas tem 20% de chance de falhar. Se falhar, o item volta intacto.
4. Traços incompatíveis não se misturam. Um gene repetido vira experiência.
5. **Evolução:** armas ganham experiência ao matar e armaduras ganham ao receber golpes. Chefes dão muito mais. Ao encher a barra, um gene sobe de nível.
6. **Purga:** um Frasco de Amostra vazio no lugar da amostra remove o último gene. O item perde metade da experiência.

Os 14 traços têm efeito próprio em armas (veneno, roubo de vida, enxame, teleporte…) e em armaduras (espinhos, regeneração, explosão reativa…). Segure Shift sobre o item para ver cada um.
Datapacks podem liberar outros itens pelas tags `atrocities:imbuable_weapons` e `atrocities:imbuable_armor`.

## Aberrações Primordiais (fim de jogo)

1. **Caça e captura:** rastreie espécies-base em biomas específicos, enfraqueça-as e use a Seringa de Extração ou a Cápsula de Estase.
2. **Biomassa e refino:** triture carne no Extrator de Biomassa para alimentar as máquinas e purifique amostras na Centrífuga Genética.
3. **Sequenciamento:** combine amostras para formar um Embrião Instável, que define arquétipo, traços e tier do chefe.
4. **Incubação:** mantenha a Incubadora Biológica alimentada com biomassa enquanto o embrião cresce no vidro. Embriões instáveis podem mutar ou abortar.
5. **Despertar e combate:** use o Embrião Maduro no chão e enfrente o chefe: Colosso Quitinoso, Matriarca Hemófaga ou Legião Coalescida, conforme o arquétipo. A entrada e cada troca de fase (a 66% e a 33% de vida) são cutscenes que congelam a arena. Cada uma das três fases tem uma mecânica própria para derrotar o chefe. As Aberrações Primordiais têm de 900 a 3.000 de vida, conforme o tier e os traços. Cada golpe é avisado por um telegrafo no chão; quando o chefe fica atordoado, ele está vulnerável.
6. **Loot:** cada chefe deixa Corações Aberrantes (um por tier) e seu troféu. Com eles você faz a Marreta de Carapaça, o Cutelo Sanguíneo ou o Lança-Bile.

## Códex Atrocitas

Todo jogador recebe o **Códex Atrocitas** ao entrar no mundo pela primeira vez. É um livro-guia com receitas, espécies, máquinas, genética e armas.

As fichas dos chefes ficam secretas, só com uma silhueta, até você enfrentar o chefe. Depois mostram o modelo 3D animado, as fases, os golpes (clique para ver cada um), os drops e os registros. Abater o chefe libera as notas de dissecação.

O conteúdo vem de resource packs: `assets/<ns>/codex/chapters/*.json` e `assets/<ns>/codex/entries/<capítulo>/*.json`.

## Roadmap

| Fase | Versão | Status |
|---|---|---|
| 1. Base, registros e VersionGate | 0.1.0 | ✅ |
| 2. Sistema genético (Data Components, seringa, cápsula) | 0.2.0 | ✅ |
| 3. Máquinas e incubação | 0.3.0 | ✅ |
| 4. Entidades, IA e animações GeckoLib | 0.4.0 | ✅ |
| 5. Loot e balanceamento | 1.0.0 | ✅ |
| 6. Feedback de combate, tooltips customizadas e rebalanceamento | 1.1.0 | ✅ |
| 7. Chefes cinematográficos: cutscenes de entrada, fases com transição e mecânicas únicas | 1.2.0 | ✅ |
| 8. Códex Atrocitas: livro-guia ilustrado com páginas secretas dos chefes | 1.3.0 | ✅ |
| 9. Mutantes: soros, mutação em estágios, cinco mutantes e relíquias | 1.4.0 | ✅ |
| 10. Imbuição genética de armas: terminal, tanques e traços evoluíveis | 1.5.0 | ✅ |
| 11. Mais mutantes (Enderman, Slime, Bruxa...), armaduras mutantes e surtos | 1.6.0 | ⏳ |

Veja o [CHANGELOG](CHANGELOG.md) para o histórico completo.

## Genética por datapack

- `data/<ns>/atrocities/mutation/<id>.json`: quais mobs (`sources`) viram qual mutante (`result`), e a cor das partículas (`color`). Funciona com mobs de outros mods.

Espécies e traços são JSON e podem ser alterados ou ampliados por datapack, inclusive com criaturas de outros mods:

- `data/<ns>/atrocities/species/<id>.json`: entidade, arquétipo (`chitinous`, `hemophage`, `coalesced`), cor, limites de vida para seringa e cápsula, pureza base e traços doados (peso e faixa de potência).
- `data/<ns>/atrocities/trait/<id>.json`: categoria, potência máxima, instabilidade por nível, cor, traços incompatíveis e `boss_attributes` (bônus de atributo do chefe por nível).

- `data/<ns>/data_maps/item/biomass_yield.json`: quanto cada item rende no Extrator de Biomassa (100 = 1 Biomassa).

Exemplo: [`species/ravager.json`](src/main/resources/data/atrocities/atrocities/species/ravager.json).

## Loot por datapack

- `data/atrocities/loot_table/entities/<chefe>.json`: loot de cada chefe.
- A condição `{"condition": "atrocities:boss_tier", "min": 3}` limita um pool aos chefes de tier III ou mais (`max` também é aceito).

## Configuração do servidor

`config/atrocities-common.toml`:

| Opção | Padrão | Descrição |
|---|---|---|
| `attributes.maxHealthCap` | 1000000 | Teto do atributo de vida máxima (vanilla = 1024) |
| `bossCombat.hitSoftCapThreshold` | 40 | Dano por hit a partir do qual o soft cap atua |
| `bossCombat.hitSoftCapFactor` | 0.6 | Fração do excedente que passa pelo soft cap |
| `bossCombat.bossHealthMultiplier` | 1.0 | Multiplica a vida dos chefes |
| `bossCombat.bossDamageMultiplier` | 1.0 | Multiplica o dano dos chefes |
| `bossCombat.broadcastBossEvents` | true | Anuncia no servidor quando um chefe desperta ou morre |
| `codex.giveOnFirstJoin` | true | Entrega o Códex Atrocitas na primeira entrada de cada jogador |
| `versionGate.kickClientsWithoutMod` | true | Desconecta quem não tem o mod, mostrando o link de download |
| `versionGate.requireExactVersion` | false | Exige a mesma versão do servidor |
| `versionGate.githubRepo` | KoZuuYT/Atrocities | Repositório usado nos links de download |

## Compilar

```powershell
powershell -ExecutionPolicy Bypass -File .\build-and-export.ps1   # jar em libs/ (e no perfil do jogo, se houver local.properties)
powershell -ExecutionPolicy Bypass -File .\release.ps1            # publica a Release no GitHub
```

## Licença

All Rights Reserved © kozzy.
