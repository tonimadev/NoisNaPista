# CLAUDE.md — FIDD (app Android)

FIDD (Ferramenta Inteligente de Detecção de Danos) detecta buracos automaticamente pelo acelerômetro
+ GPS enquanto a pessoa dirige, e mostra num mapa colaborativo. Kotlin 2.4, Jetpack Compose,
Hilt, Room, WorkManager, Retrofit/Moshi, Navigation 3, AGP 9 (Kotlin embutido — os módulos não
aplicam `kotlin.android`). Backend: `~/IntelliJIdeaProjects/NoisNaPistaBackend` (repo separado, tem
o próprio CLAUDE.md). UI e textos do app em **pt-BR**.

## Comandos

```bash
./gradlew assembleDebug                 # APK debug
./gradlew testDebugUnitTest :core:model:test   # todos os testes unitários (JVM + Robolectric)
./gradlew koverHtmlReportCoverage       # cobertura agregada -> build/reports/kover/htmlCoverage/index.html
./gradlew koverVerifyCoverage           # gate: falha abaixo de 90% de linhas / 75% de branches
./gradlew :feature:tracker:impl:testDebugUnitTest --tests "*HistoryScreenTest"   # um teste só
./gradlew detekt spotlessApply sortDependencies   # estilo (mesmo gate do Kairos) — rodar antes de commitar
```

Estilo: ktlint 1.2.1 via Spotless (`spotless.gradle`, regras no `.editorconfig`, 120 colunas), detekt com
`config/detekt/detekt.yml` (máx. 11 funções top-level por arquivo: tela grande se divide por assunto, ex.
`DebugDetailScreen.kt`) e `sortDependencies` ordenando `plugins {}`/`dependencies {}`. Corrija o código em vez
de afrouxar essas configs. `.claude/` fica fora do Spotless (worktrees de outros branches).

`secrets.properties` (gitignored) guarda `MAPS_API_KEY`; sem ele o build usa o placeholder de
`local.defaults.properties` e o mapa fica sem tiles.

## Arquitetura

```
app/                   Shell: MainActivity (NavigationSuiteScaffold + NavDisplay), tema, FiddApp (Hilt + WorkManager)
core/model             Data classes puras (módulo JVM, sem Android)
core/ui                Recursos visuais compartilhados (PotholeMarker)
core/location          LocationProvider (interface) + AndroidLocationProvider (Fused Location)
core/sensor            MotionSensor + PotholeDetector (máquina de detecção) + TrackingService (foreground)
core/data              Room, repositórios, DataStore (preferências/identidade), SyncWorker
core/network           Retrofit + DTOs Moshi do backend
core/billing           RemoveAdsRepository: compra única remove_ads_premium via lib PayWall (JitPack)
core/ads               SDK do AdMob isolado: SponsoredBanner (banner + convite "Remover anúncios") e init
core/analytics         Firebase Analytics isolado: AnalyticsTracker + eventos tipados (AnalyticsEvent)
core/testing           Fakes e utilitários de teste compartilhados (só testImplementation)
feature/<x>/bridge     NavKey da feature (@Serializable, sem UI) — o que outros módulos podem ver
feature/<x>/impl       Telas Compose + ViewModels
feature/onboarding     Onboarding de primeira execução
ml/                    Pipeline Python offline de classificação (não roda no app)
```

Regras de dependência:
- `feature:*:impl` depende de `core:*` e do próprio `bridge`; **nunca** de outro `impl`. A navegação
  entre features passa pelo `app` usando as `NavKey`s dos `bridge`.
- `core:model` não depende de Android. `core:*` não depende de `feature:*`.
- Interfaces nos `core` (`PotholeRepository`, `CityRepository`, `LocationProvider`, `MotionSensor`)
  com implementação ligada por módulo Hilt (`DataModule`, `LocationModule`, `SensorModule`).

Padrão de tela (MVI):
- ViewModel `@HiltViewModel` expõe `StateFlow<XUiState>` (ou StateFlows por campo) e um
  `Channel<XUiEffect>` recebido como Flow para efeitos únicos (toast/snackbar/navegação); recebe
  ações por `onIntent(XUiIntent)` quando há várias.
- A tela recebe o ViewModel por parâmetro (`fun XScreen(viewModel: XViewModel, ...)`) e o `app`
  passa `hiltViewModel()`. Estado lido com `collectAsStateWithLifecycle()`.
- Todo texto visível vai em `res/values/strings.xml` do módulo (pt-BR), nunca literal no código.

## Requisitos de produto (não violar)

- **Detecção 100% automática** pelos sensores. O usuário rejeitou um botão de "adicionar buraco
  manualmente". A única intervenção manual é no Histórico: marcar alarme falso ou apagar.
- `PotholeDetector` (singleton) é a fonte de verdade de "detecção ativa". A UI nunca seta
  `isTracking` otimisticamente — espelha `potholeDetector.isTracking`. Sensores/GPS só começam em
  `startDetection()` (chamado por `TrackingService.onCreate`), nunca no `init` — senão
  `SecurityException` antes da permissão.
- Parâmetros de detecção (`IMPACT_THRESHOLD` 15 m/s², `COOLDOWN_NANOS` 4 s, `MIN_SPEED_MPS`,
  limite de rotação do giroscópio) vêm de análise de dados reais — não altere sem dado novo e sem
  atualizar os comentários que justificam o valor. Gates falham "aberto": sem velocidade/gravidade/
  giroscópio, a detecção não é bloqueada.
- **Offline-first**: detecção grava no Room na hora; `SyncWorker` envia em lotes de
  `PotholeReadingBatchRequestDto.MAX_SIZE`. `syncPotholes()` **lança** se algum lote falhar, para o
  WorkManager tentar de novo. Leitura rejeitada pelo servidor fica pendente, sem retry imediato.
- **Anonimato**: a identidade é um UUID local (`ReporterIdentityProvider`) enviado em
  `X-Reporter-Token`. Nada de dado pessoal no backend. A janela bruta de sensores
  (`SensorWindow`) é só local, nunca sincronizada.
- Falhas de rede na carga automática são silenciosas; só a ação explícita do usuário (atualizar,
  votar) mostra mensagem de erro. O botão "Atualizar" da comunidade tem que existir mesmo com a
  lista vazia.
- A tela Debug/classificação só existe em build debug (`BuildConfig.DEBUG` no `MainActivity`).
- **Anúncios: só AdMob, só banner** (decisão do usuário). Nunca com a detecção ativa (pessoa
  dirigindo), nem no onboarding/Debug; sem banner enquanto a Play não disse se comprou (`adsRemoved`
  null, com carência de 5 s). O `AdsViewModel` do app decide e passa `adBanner` às telas como slot
  nulável; features não dependem do SDK. Debug usa sempre o banner de teste do Google (`AdUnits`).
  IDs reais vão no `admob.properties` (raiz, git-ignored): `ADMOB_APP_ID` e um banner por tela
  (`ADMOB_BANNER_HOME`/`_MAP`/`_HISTORY`/`_RANKING`, ver `AdPlacement`); sem eles, IDs de teste.
- **Analytics/Crashlytics anônimos**: só comportamento dentro do app. Todo evento é um `AnalyticsEvent`
  (parâmetros só enums/números arredondados) — nunca coordenada, id de buraco, token de reporter, texto
  digitado, `setUserId` ou user property. Advertising ID/SSAID e sinais de anúncio desligados no manifest de
  `core:analytics`. Coleta (Analytics e Crashlytics) só no release (`FIREBASE_COLLECTION_ENABLED`).
  `app/google-services.json` é git-ignored: sem ele o build falha.
- O usuário recusou um toggle "celular no suporte vs. na mão" (exigiria migração) — o campo de
  observação livre do Debug cobre isso. Não repropor sem informação nova.

## Armadilhas conhecidas

- **Hilt + WorkManager**: o `AndroidManifest` remove o `WorkManagerInitializer` do
  `InitializationProvider` para o WorkManager usar o `HiltWorkerFactory` de `FiddApp`. Sem isso o
  `SyncWorker` falha com `NoSuchMethodException` e a sincronização morre em silêncio.
- DTOs de rede usam `@JsonClass(generateAdapter = true)` (Moshi codegen), **não**
  `@Serializable` — o Retrofit está com `MoshiConverterFactory`.
- `BASE_URL` fica em `core/network/.../NetworkModule.kt`. Para backend local: troque para
  `http://localhost:8080/` e rode `adb reverse tcp:8080 tcp:8080` (refazer a cada emulador/aparelho).
- Room está com `fallbackToDestructiveMigration(true)` ("pré-lançamento"). Com o app publicado,
  mudar o schema apaga os dados locais do usuário — qualquer mudança de entidade deve vir com
  `Migration` e bump de versão.
- Um `<vector>` sem nenhum `<path>` compila mas quebra o ícone em runtime — use um path transparente.
- `minSdk` 24: APIs Java/Android acima disso precisam de checagem de versão ou desugaring.
- `NavDisplay` guarda o conteúdo de cada `NavEntry`: valor que muda (ex. `adBanner`) tem que ser lido
  via `State` (`rememberUpdatedState`) dentro da entrada, senão fica congelado no da 1ª composição.
- O `MobileAdsInitProvider` do AdMob derruba o processo sem `APPLICATION_ID` no manifest (daí o
  placeholder de teste no `app/build.gradle.kts`). Em teste, `AdMobBanner` sob `LocalInspectionMode`
  para não disparar `loadAd` na JVM.

## Testes

Stack: JUnit4, kotlinx-coroutines-test, Turbine, MockK (só para o que não dá para fakear, ex.
`LocationServices`), Robolectric (SDK fixado em 35 por `src/test/resources/robolectric.properties`),
Compose UI Test sob Robolectric, MockWebServer, Hilt testing.

- **Prefira fakes a mocks.** `:core:testing` tem `FakePotholeRepository`, `FakeCityRepository`,
  `FakeLocationProvider`, `FakeMotionSensor`, `MainDispatcherRule`, builders `testPothole()`,
  `testLocation()`, `testCityRanking()` e `testPreferencesDataStore()` (DataStore real em pasta
  temporária). `PotholeDetector` é usado de verdade, alimentado pelos fakes.
- ViewModels: `MainDispatcherRule` + fakes. Quando o valor vem de fora do scheduler de teste
  (I/O do DataStore, `Dispatchers.Default` do `PotholeDetector`), espere com `awaitFirst {}` /
  `awaitCondition {}` de `:core:testing` — `withTimeout` dentro de `runTest` usa tempo virtual e
  `runBlocking` trava o scheduler.
- `SharedFlow` sem replay: comece a coletar antes de emitir (`CoroutineStart.UNDISPATCHED`) ou
  espere `subscriptionCount`.
- Telas: `createComposeRule()` (ou `createAndroidComposeRule<ComponentActivity>()` quando precisa
  responder pedido de permissão via `shadowOf(activity).lastRequestedPermission` +
  `onRequestPermissionsResult`). Asserte por `R.string` do módulo, não por texto literal.
  `@GraphicsMode(NATIVE)` + `onRoot().captureToImage()` força o desenho de `Canvas`.
- **GoogleMap não carrega na JVM** (sem Play Services): o conteúdo dentro de `GoogleMap { }` (pinos,
  cluster renderer) não é coberto — teste o que está em volta. `CameraUpdateFactory` precisa de
  `mockkStatic`. Depois que um mapa está na tela o looper principal fica ocupado: prepare o estado
  antes do `setContent`.
- App shell (`MainActivityTest`): `@HiltAndroidTest` + `HiltTestApplication`, com `DataModule`,
  `PreferencesModule`, `LocationModule`, `SensorModule`, `BillingModule` e `AnalyticsModule` desinstalados e
  substituídos por `@BindValue` (o DataStore real é singleton de processo e vazaria estado entre testes).
- Biblioteca sem chave do Maps no manifest: use `setMapsApiKey(app)` (testes de `feature:tracker:impl`).

Toda mudança de comportamento vem com teste, e `./gradlew koverVerifyCoverage` precisa passar.
Código gerado (Hilt, Room `_Impl`, Moshi, serializers) e `@Preview` são excluídos da cobertura em
`build.gradle.kts` da raiz.

## Convenções

- Comentários explicam o **porquê** (decisão, dado, bug que evitou); vários estão em pt-BR, outros
  em inglês — siga o idioma do arquivo.
- Novas dependências entram em `gradle/libs.versions.toml`, assim como `compileSdk`/`targetSdk`/
  `minSdk`/`java` (lidos nos módulos com `libs.versions.x.get().toInt()`) — nada de versão literal.
- Commits em inglês, imperativo, assunto descritivo (ver `git log`).
- Validar mudança de UI no emulador/aparelho além dos testes; `adb emu sensor set acceleration 0:0:<z>`
  e `adb emu geo fix <lon> <lat>` simulam impacto e posição no emulador.
