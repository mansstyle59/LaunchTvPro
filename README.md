# 📺 LaunchTVPro — Android TV

Navigateur web Android TV avec moteur de navigation D-pad spatial.  
Transforme **n'importe quel site web** en expérience télécommande TV.

---

## 🚀 Fonctionnalités

| Fonctionnalité | Détail |
|---|---|
| **Navigation D-pad** | Moteur spatial JS qui détecte automatiquement tous les éléments cliquables |
| **Focus visuel** | Contour bleu cyan animé sur l'élément actif |
| **Mode saisie** | Bascule clavier virtuel pour les champs texte |
| **Toolbar TV** | Barre rétractable avec URL, retour, avancer, accueil |
| **Bridge natif** | Communication bidirectionnelle JS ↔ Android |
| **Mémoire URL** | Reprend la dernière page visitée au lancement |
| **Plein écran** | Mode immersif optimisé 1080p TV |

---

## 🎮 Mapping Télécommande

```
┌─────────────────────────────────────┐
│         NAVIGATION D-PAD            │
│   ↑ ↓ ← →  →  Déplacer le focus     │
│   OK / Entrée  →  Activer/Cliquer   │
│   BACK         →  Page précédente   │
│   MENU         →  Toolbar ON/OFF    │
│                                     │
│         TOUCHES COULEURS            │
│   🔴 ROUGE  →  Recharger la page    │
│   🟢 VERT   →  Retour historique    │
│   🟡 JAUNE  →  Ouvrir barre URL     │
│   🔵 BLEU   →  Page d'accueil       │
│                                     │
│         AUTRES                      │
│   CH+ / CH-  →  Avant / Arrière     │
│   PAGE UP/DN →  Scroll page         │
└─────────────────────────────────────┘
```

---

## 🏗️ Architecture du projet

```
LaunchTVPro/
├── app/
│   ├── src/main/
│   │   ├── java/com/launchtvpro/
│   │   │   └── MainActivity.kt          ← Activité principale
│   │   ├── assets/
│   │   │   └── tv_navigation.js         ← Moteur navigation D-pad
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml ← Interface TV
│   │   │   ├── drawable/                ← Icônes & backgrounds
│   │   │   └── values/                  ← Thèmes, couleurs, strings
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## ⚙️ Installation & Build

### Prérequis
- **Android Studio** Hedgehog (2023.1.1) ou supérieur
- **JDK 17**
- **SDK Android** API 21–34

### Étapes

```bash
# 1. Ouvrir dans Android Studio
File → Open → sélectionner le dossier LaunchTVPro/

# 2. Sync Gradle
Tools → Sync Project with Gradle Files

# 3. Déployer sur Android TV ou émulateur TV
Run → Select Device → Android TV (1080p) API 34
```

### Émulateur Android TV (sans device physique)
```
AVD Manager → Create Virtual Device
→ TV → TV 1080p
→ System Image: API 34 (x86_64)
→ Finish
```

---

## 🧠 Fonctionnement du moteur TVNav (tv_navigation.js)

### Algorithme de navigation spatiale

```
1. getFocusable()
   └── Collecte tous les éléments interactifs visibles :
       a[href], button, input, select, [role="button"], etc.

2. navigate(direction)
   └── Pour chaque élément candidat :
       ├── Calcule le vecteur (dx, dy) vers la cible
       ├── Vérifie que l'élément est dans un cône de 90°
       │   dans la direction demandée
       └── Score = distance + pénalité angulaire
           → Sélectionne le score le plus bas

3. setFocus(element)
   ├── Ajoute la classe .__tvnav_focus (CSS highlight)
   ├── Appelle element.focus()
   ├── Scroll vers l'élément si hors écran
   ├── Affiche le tooltip de description
   └── Notifie le bridge Android via TVBridge
```

### Bridge JavaScript ↔ Android

```kotlin
// Android → JS
webView.evaluateJavascript("window.TVNav.navigate('right')", null)
webView.evaluateJavascript("window.TVNav.activate()", null)

// JS → Android  
window.TVBridge.onFocusChanged(tagName, label)
window.TVBridge.onInputModeChanged(true/false)
window.TVBridge.navigateTo(url)
```

---

## 🎨 Personnalisation

### Changer la couleur de focus
Dans `tv_navigation.js`, modifier `CONFIG.FOCUS_COLOR` :
```js
FOCUS_COLOR: '#00D4FF',  // Cyan par défaut → ex: '#FF6B35' (orange)
```

### Changer la page d'accueil
Dans `MainActivity.kt` :
```kotlin
private const val DEFAULT_HOME = "https://www.google.com"
```

### Ajouter des éléments focusables
Dans `tv_navigation.js`, ajouter au tableau `CONFIG.SELECTOR` :
```js
'[data-clickable]', '.my-custom-button'
```

---

## 📦 Générer l'APK de release

```bash
# Dans Android Studio
Build → Generate Signed Bundle/APK
→ APK → Next
→ Créer/sélectionner un keystore
→ Release → Create

# Ou en ligne de commande
./gradlew assembleRelease
# APK dans : app/build/outputs/apk/release/
```

---

## 🔧 Dépannage

| Problème | Solution |
|---|---|
| Navigation ne marche pas | Vérifier que JS est activé dans WebSettings |
| Focus invisible | Certains sites réinitialisent `outline: none`, le script le réinjecte |
| Clavier ne s'affiche pas | Appuyer sur 🟡 JAUNE ou aller dans la barre URL |
| Site refuse WebView | Modifier le `userAgentString` dans `setupWebView()` |
| Crash sur API < 21 | `minSdk` est fixé à 21, OK pour 99%+ des TV Android |

---

## 📄 Licence

MIT — Libre d'utilisation, modification et distribution.

---

*LaunchTVPro — Compatible Google TV, Fire TV (avec sideload), et tous les STB Android 5.0+*
