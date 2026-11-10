# Changelog

Todas as mudanças relevantes do **Atrocities** ficam registradas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versões seguem [SemVer](https://semver.org/lang/pt-BR/).
Cada versão tem o `.jar` correspondente nas [Releases do GitHub](https://github.com/KoZuuYT/Atrocities/releases).

## [Não lançado]

## [1.1.0] - 2026-11-10 — Fase 6: Feedback, tooltips e rebalanceamento

### Adicionado
- **Telegrafo dos golpes:** enquanto um chefe carrega um golpe:
  - partículas convergem para o corpo dele;
  - o modelo treme e incha cada vez mais;
  - um som sobe de tom;
  - aparece um aviso de perigo no chão: em golpes de área, um anel interno cresce até o externo; em investidas e disparos, uma linha tracejada vai até o alvo.
- **Golpe liberado:** clarão, estalo e um leve tremor de tela no instante do impacto.
- **Tremor de câmera** no despertar do chefe, nos impactos que racham o chão, nas ondas de choque, no frenesi e na explosão final.
- **Feedback de dano no chefe:**
  - respingo de sangue do lado atingido e som de carne;
  - o corpo amassa a cada golpe;
  - números de dano flutuantes (brancos, dourados com "!" quando vulnerável, verdes quando ele se cura).
- **Atordoamento:** aviso "VULNERÁVEL! Ataque agora!" na tela, anel de faíscas sobre o chefe e som de crítico a cada golpe.
- Rastro de sangue durante o salto (Bote) da Matriarca.
- **Tooltips customizadas** para os itens do mod:
  - moldura na cor da raridade, com cantos ornamentados e linha sob o nome;
  - selos de raridade (Comum, Incomum, Raro, Épico, Lendário, Artefato), de categoria e de tier (nos embriões);
  - Lendário: moldura e nome cintilando, com brilhos piscando;
  - Artefato: moldura e nome pulsando, com sangue escorrendo da borda.
- Roadmap das próximas fases (7 a 10) no README.

### Alterado
- **Chefes mais fracos:**
  - Vida por tier caiu de 1.500 / 2.500 / 3.500 / 4.500 para 900 / 1.400 / 2.000 / 2.700, com teto de 3.000 (antes 5.000).
  - Dano base caiu de 14 / 18 / 23 / 28 para 11 / 14 / 17 / 21.
  - Bônus de dano no frenesi caiu de +15% para +10%.
  - Armadura menor: Colosso 8 (antes 12), Matriarca 4 (antes 6), Legião 2 (antes 4).
- **Soft cap mais brando:** começa em 40 de dano (antes 30) e deixa passar 60% do excedente (antes 35%). Um golpe de 48 agora causa 44,8 (antes 36,3).
  - As opções foram renomeadas para `hitSoftCapThreshold` e `hitSoftCapFactor`, então configurações antigas passam a usar os valores novos automaticamente.

## [1.0.0] - 2026-11-10 — Fase 5: Loot e balanceamento

### Adicionado
- **Loot dos chefes**, em tabelas de loot por datapack (`data/atrocities/loot_table/entities/<chefe>.json`):
  - **Coração Aberrante**: 1 por tier (I = 1, IV = 4).
  - Troféu do chefe: 2 a 3, mais 1 a 2 a partir do tier III.
    - **Placa de Carapaça** (Colosso).
    - **Presa da Matriarca** (Matriarca).
    - **Núcleo da Legião** (Legião).
  - Biomassa, Espinhos Quitinosos (Colosso) e Bolotas de Bile (Legião).
  - Frascos de Amostra a partir do tier II.
- Condição de loot `atrocities:boss_tier` (`min`/`max`), para datapacks darem loot conforme o tier do chefe.
- **Coração Aberrante** comestível: Absorção IV (2 min), Regeneração II (10 s), Força I (1 min) e Náusea (8 s). Também conserta as armas novas na bigorna.
- **Armas feitas dos troféus**, mais duráveis que netherite e encantáveis:
  - **Marreta de Carapaça**: 48 de dano, lenta. Botão direito no chão: Impacto Sísmico, 30 de dano ao redor e empurrão, com recarga de 8 s.
  - **Cutelo Sanguíneo**: 40 de dano, rápido. Cura 15% do dano causado, até 8 de vida por golpe.
  - **Lança-Bile**: dispara Bolotas de Bile (16 de dano e nuvem tóxica), gastando uma Bolota por tiro.
- Espinhos Quitinosos (12 de dano) e Bolotas de Bile (6 de dano e nuvem tóxica) agora podem ser arremessados.
- Receitas das três armas, texturas, traduções e tags de encantamento.

### Alterado
- **Chefes mais fracos**:
  - Vida por tier caiu de 3.000 / 6.000 / 10.000 / 15.000 para 1.500 / 2.500 / 3.500 / 4.500.
  - Teto de 5.000 de vida mesmo com os traços (antes do `bossHealthMultiplier`).
  - Dano base caiu de 18 / 24 / 32 / 42 para 14 / 18 / 23 / 28.
- Projéteis dos jogadores acertam chefes e crias; os dos chefes continuam sem acertar o próprio chefe.

### Corrigido
- Chefe invocado por `/summon` com genoma na NBT agora nasce com a vida cheia.

## [0.4.0] - 2026-11-10 — Fase 4: Entidades, IA e GeckoLib

### Adicionado
- **Três chefes animados com GeckoLib**, um por arquétipo. Cada um tem modelo próprio, textura com partes que brilham no escuro e 11 animações (parado, andando, emergindo, morrendo, atordoado, rugido e um golpe por animação).
  - **Colosso Quitinoso** (quitinoso): Esmagamento, Varredura, Investida, Saraivada de Espinhos e, na fase 2, Terremoto. Se a Investida bater numa parede, ele fica atordoado.
  - **Matriarca Hemófaga** (hemófago): Dilacerar (cura com cada vítima), Bote, Jato de Sangue, Chamado da Ninhada e, na fase 2, Hemorragia, que drena todos ao redor e a deixa saciada (atordoada) depois.
  - **Legião Coalescida** (coalescido): Engolfar, Explosão de Esporos, Atração, Brotamento (fica exposta depois), Morteiro de Bile e, na fase 2, Fissão em várias crias.
- **Despertar**: o **Embrião Maduro**, usado no chão, faz o chefe do seu genoma emergir da terra em 3 segundos, invulnerável. Não funciona no Pacífico nem sem espaço livre.
- **O genoma define o chefe**:
  - Tier I a IV: vida de 3.000 / 6.000 / 10.000 / 15.000, dano base de 18 / 24 / 32 / 42 e tamanho de 85% a 125%.
  - Os traços aplicam bônus de atributo vindos do novo campo `boss_attributes` dos JSONs de traço, ajustável por datapack.
  - Os traços também têm efeitos próprios: veneno, sangramento, náusea, fogo, cegueira e aura de Escuridão nos golpes; regeneração; espinhos que devolvem dano; nuvens de esporos; crias periódicas; teletransporte; e explosão ao morrer.
- **Combate de chefe**:
  - Golpes com aviso antes do impacto e tempo de recarga.
  - **Fase 2** a 50% de vida: rugido com onda de choque, mais velocidade, mais dano, golpes novos e tela escurecida.
  - **Janelas de vulnerabilidade** (atordoado): o dano ignora o soft cap.
  - Barra de chefe na cor do arquétipo com o tier, imunidade a queda, sufocamento, veneno e Wither, e crias da linhagem que não atacam o chefe.
  - Animação de morte de 3,5 s, explosão de carne e anúncio no servidor quando o chefe desperta ou morre.
- Projéteis orgânicos: Espinho Quitinoso e Bolota de Bile (vira nuvem tóxica).
- Tooltip do embrião mostra qual chefe ele desperta.
- 39 sons novos com legendas, traduções em `pt_br` e `en_us` e a tag `c:bosses`.
- Configuração: `bossCombat.bossHealthMultiplier`, `bossCombat.bossDamageMultiplier` e `bossCombat.broadcastBossEvents`.
- `build-and-export.ps1` também copia o jar para o perfil do jogo indicado em `local.properties` (`instance_mods_dir=...`, arquivo local fora do Git) e apaga as versões antigas do Atrocities de lá.

### Alterado
- Traços aceitam o campo opcional `boss_attributes`. Datapacks antigos continuam válidos.

## [0.3.0] - 2026-11-10 — Fase 3: Máquinas e Incubação

### Adicionado
- **Quatro máquinas biológicas**, com interface própria, barras animadas, partículas, sons e suporte a funis:
  - Funis inserem nas entradas e no combustível e retiram só das saídas.
  - O conteúdo cai no chão quando a máquina é quebrada.
- **Extrator de Biomassa**: tritura matéria orgânica num acumulador; cada 100 pontos viram 1 **Biomassa**. Não precisa de combustível.
- **Biomassa**: combustível das outras máquinas. Cada uma vale 100 nutrientes, e a reserva comporta até 2.000.
- **Data map `atrocities:biomass_yield`** (`data/<ns>/data_maps/item/biomass_yield.json`) define quanto cada item rende.
  - Vem com 33 itens, de carne podre (25) a catalisador de sculk (200).
  - O tooltip de cada item mostra quanto ele rende.
- **Centrífuga Genética**:
  - **Uma amostra:** ganha de +6 a +12 de pureza, até 90%.
  - **Duas amostras da mesma espécie:** fundem-se. A pureza fica igual à maior mais 1/3 da menor, até 100%, e os traços ficam com a maior potência. Sobra um frasco vazio.
  - A tela mostra a pureza que vai resultar.
- **Sequenciador Genético**: une de 2 a 4 amostras num **Embrião Instável**. A tela mostra antes o tier e a estabilidade que o embrião vai ter.
- **Incubadora Biológica**: gesta o embrião até virar **Embrião Maduro**, que você vê flutuando no fluido do tanque, pulsando no ritmo do coração.
  - Tempo por tier: 1, 2, 3 e 5 minutos. Gasta 1 nutriente a cada 2 ticks.
  - **Fome:** sem nutrientes, o progresso para e o embrião perde 1 de estabilidade a cada 5 segundos.
  - **Aborto:** com estabilidade abaixo de 35%, há 2% de chance de aborto por ponto abaixo, até 70%. O embrião se perde e até 3 criaturas das espécies que o formaram saem do tanque, hostis.
  - **Mutação:** chance de (100 − estabilidade)/2 %. A mutação fortalece um traço ou desperta um traço latente da linhagem, e custa 5 de estabilidade. O tier nunca cai.
- **Embrião Maduro**: guarda o genoma final do chefe, que será despertado na Fase 4.
- Tooltips do embrião passam a mostrar o tempo de incubação e as mutações.
- Receitas das quatro máquinas, onze sons novos com legendas e traduções em `pt_br` e `en_us`.

### Alterado
- O genoma do embrião ganhou o campo `mutations`. Embriões criados na 0.2.0 continuam válidos.

## [0.2.0] - 2026-11-10 — Fase 2: Sistema Genético

### Adicionado
- **Registros de datapack** `atrocities:species` e `atrocities:trait`, sincronizados com os clientes. Qualquer criatura, inclusive de outros mods, pode virar espécie com um JSON em `data/<ns>/atrocities/species/`.
- **14 traços genéticos**: Placas de Quitina, Espinhos de Quitina, Tecido Regenerativo, Hipertrofia, Hemotoxina, Anticoagulante, Bile Tóxica, Glândulas Voláteis, Surto Adrenal, Fios do Vazio, Membranas Noturnas, Bolsas de Esporos, Mente de Colmeia e Órgãos Térmicos. Cada um tem categoria, potência máxima, instabilidade e incompatibilidades.
- **14 espécies-base** em três arquétipos:
  - **Quitinoso:** aranha, aranha da caverna, traça e devastador.
  - **Hemófago:** zumbi, zumbi do deserto, afogado, bruxa e phantom.
  - **Coalescido:** slime, cubo de magma, endermite, vex e creeper.
- **Data Components**:
  - `gene_sample`: espécie, pureza e traços.
  - `embryo_genome`: arquétipo, tier, estabilidade, traços e linhagem.
  - `captured_specimen`: a criatura inteira guardada.
- **Seringa de Extração** (64 usos): numa criatura enfraquecida abaixo do limite da espécie, enche um Frasco de Amostra e gera uma **Amostra Genética**.
  - Quanto mais ferido o alvo, mais pura a amostra.
  - A agulha fere o alvo sem matá-lo, então ele revida.
  - Pureza de 85% ou mais rende uma amostra excepcional.
- **Cápsula de Estase**: lacra viva uma criatura muito ferida e a libera ao usar num bloco.
  - O espécime guarda vida, equipamento e nome.
  - Criaturas liberadas não somem com a distância.
  - Jogadores e chefes não podem ser capturados.
- **Embrião Instável** com a lógica de sequenciamento pronta para a Fase 3. O genoma combina amostras:
  - **Arquétipo:** o dominante, ponderado pela pureza.
  - **Traços:** potências somadas e limitadas ao máximo de cada traço.
  - **Estabilidade:** penalizada por conflitos, hibridização e impureza.
  - **Tier I–IV:** define a vida base do chefe (3.000 / 6.000 / 10.000 / 15.000).
- **Tooltips** com espécie, arquétipo, barras de pureza e estabilidade, tier, vida estimada do chefe e traços em algarismos romanos. Segurar Shift mostra a categoria e a descrição de cada traço e a linhagem.
- **Visual e som**:
  - O fluido das amostras e da cápsula ganha a cor da espécie, e o núcleo do embrião a cor do arquétipo.
  - Partículas: filete de sangue até a mão, espiral de contenção, flash de captura e liberação.
  - Seis sons novos com legendas.
- Receitas da Seringa de Extração e da Cápsula de Estase.
- Exemplos na aba criativa: uma amostra de referência por espécie e um embrião por arquétipo.
- Log na inicialização do servidor com o total de espécies e traços carregados, e aviso para traços inexistentes.

### Corrigido
- Acentuação de três textos da configuração em `pt_br`.

## [0.1.0] - 2026-11-10 — Fase 1: Base e Registros

### Adicionado
- Projeto NeoForge 21.1.265 (Minecraft 1.21.1, Java 21) com **GeckoLib 4.9.3** como dependência obrigatória.
- Estrutura central de registros (`ModRegistries`): blocos, itens, Data Components, block entities, entidades, menus, sons, partículas, efeitos, receitas, abas criativas e attachments, todos via `DeferredRegister`.
- Aba criativa **Atrocities**, que recebe automaticamente todo item do mod.
- Item **Frasco de Amostra** (`atrocities:sample_vial`): base da cadeia genética. Receita: 1 lingote de cobre, 1 pepita de ferro e 4 vidros, rendendo 3 frascos.
- **Teto de vida elevado:** o atributo `max_health` deixa de travar em 1024 (padrão: 1.000.000, configurável) para permitir chefes de 3.000 a 15.000+ HP.
- **Mitigação de dano dos chefes** (`DamageMitigation`): soft cap configurável para o meta de ~50 de dano por hit (padrão: limiar 30 e fator 0,35, ou seja, um hit de 50 vira 37).
- **VersionGate**, o sistema de controle de versão entre servidor e cliente:
  - Quem entra **sem o mod**, seja com cliente vanilla ou com NeoForge sem o Atrocities, é desconectado antes da sincronização de registros, com uma mensagem em PT/EN e o link direto do `.jar` da versão do servidor.
  - Quem entra com **outra versão** vê uma tela de atualização com o botão **Baixar e instalar**. O jar é baixado do GitHub, validado e instalado automaticamente quando o jogo fecha, sem nunca carregar duas versões ao mesmo tempo.
  - Opção `requireExactVersion`: desconecta versões diferentes e mostra a tela de atualização no lugar da tela de desconexão.
  - O menu principal mostra um aviso quando existe uma Release mais nova no GitHub.
- Configurações `atrocities-common.toml` (balanceamento e VersionGate) e `atrocities-client.toml` (atualizações), editáveis também pela tela de configuração do jogo.
- Traduções `pt_br` e `en_us`.
- Script `build-and-export.ps1`, que compila o mod e exporta o jar para `libs/`.
- Script `release.ps1`, que compila, exporta e publica a Release no GitHub com as notas deste changelog.

[Não lançado]: https://github.com/KoZuuYT/Atrocities/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/KoZuuYT/Atrocities/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/KoZuuYT/Atrocities/compare/v0.4.0...v1.0.0
[0.4.0]: https://github.com/KoZuuYT/Atrocities/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/KoZuuYT/Atrocities/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/KoZuuYT/Atrocities/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/KoZuuYT/Atrocities/releases/tag/v0.1.0
