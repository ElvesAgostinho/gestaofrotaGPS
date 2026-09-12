# AutoCare — App Android

App Android **nativa em Java + XML**. Sem Expo / React Native / Flutter.

## Requisitos

- Android Studio (recomendado) **ou** JDK 17+ e Android SDK via linha de comandos
- Android SDK: `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`

## Abrir e correr

**Android Studio:** abrir a pasta `mobile/`, deixar sincronizar o Gradle, escolher
um emulador (API 24+) ou dispositivo e correr.

**Linha de comandos:**

```bash
./gradlew :app:assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` (gerado, não versionado) aponta para o Android SDK. Ajustar
`sdk.dir` se necessário.

## URL da API

`app/build.gradle` define `API_BASE_URL`:

| Ambiente | Valor |
|---|---|
| Emulador Android | `http://10.0.2.2:8080/api/v1/` (predefinido, `debug`) |
| Dispositivo físico | `http://<IP-do-PC>:8080/api/v1/` |
| Produção | `https://api.autocare.ao/api/v1/` (`release`) |

O backend tem de estar a correr (`cd ../backend && mvn spring-boot:run`).

## Estrutura

```
app/src/main/
  AndroidManifest.xml
  java/ao/autocare/app/
    AutoCareApp.java
    data/
      SessionManager.java        tokens + utilizador (EncryptedSharedPreferences)
      AuthRepository.java         chamadas de autenticação fora da thread principal
      ApiException.java
      api/ApiClient.java          Retrofit + OkHttp (auth header + refresh automático)
      api/AutoCareApi.java        endpoints
      api/dto/                    DTOs de rede
    ui/
      SplashActivity.java         decide o primeiro ecrã
      auth/                       AuthActivity, LoginFragment, RegisterFragment
      onboarding/OnboardingActivity.java  (ViewPager2, 4 páginas)
      main/                       MainActivity + Dashboard/Vehicles/Map/Alerts/Profile
  res/
    values/     colors, strings (pt-AO), themes, dimens
    values-night/themes.xml
    layout/ · drawable/ · menu/ · navigation/ · mipmap-*
```

## Estado — Fase 1

Autenticação completa (registo, login, sessão persistente cifrada, logout, refresh
automático), onboarding, tema claro/escuro e navegação de 5 separadores. Os
módulos de viaturas, manutenção, documentos, despesas, alertas e GPS chegam nas
fases seguintes — os ecrãs mostram claramente a fase correspondente, sem dados
falsos. O separador **Mapa** mostra "GPS não configurado" enquanto não houver um
provedor GPS real.

## Ícone da aplicação

O ícone atual (`res/mipmap-*` e `res/drawable/ic_launcher_foreground.xml`) é um
marcador provisório. Substituir pelo **Image Asset Studio** do Android Studio.
