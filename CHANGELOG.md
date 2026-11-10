# Changelog

Todas as mudanças relevantes do **Atrocities** ficam registradas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versões seguem [SemVer](https://semver.org/lang/pt-BR/).
Cada versão tem o `.jar` correspondente nas [Releases do GitHub](https://github.com/KoZuuYT/Atrocities/releases).

## [Não lançado]

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

[Não lançado]: https://github.com/KoZuuYT/Atrocities/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/KoZuuYT/Atrocities/releases/tag/v0.1.0
