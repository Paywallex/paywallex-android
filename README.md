# Paywallex pour Android

Kit officiel Paywallex pour Android 6 ou plus récent (Kotlin). Il ouvre la page Paywallex dans un onglet personnalisé (Custom Tabs), qui garde la connexion Paywallex de Chrome. La personne paie avec son compte en un geste ou avec Mobile Money, puis revient dans votre app.

> Avant de livrer une commande, confirmez toujours depuis votre serveur (webhook `payment.succeeded` ou API `/v1`). Le retour dans l'app seul ne prouve rien.

## Installation

Ajoutez JitPack à `settings.gradle.kts` :

```kotlin
dependencyResolutionManagement { repositories { google(); mavenCentral(); maven("https://jitpack.io") } }
```

Puis la dépendance :

```kotlin
implementation("com.github.Paywallex:paywallex-android:1.0.0")
```

Déclarez votre schéma sur l'activité de retour dans le manifeste (`<data android:scheme="monapp" />` dans un `intent-filter` VIEW, DEFAULT, BROWSABLE).

## Encaisser un paiement

Le paiement se fait en deux temps : ouvrir la page, puis lire le retour.

```kotlin
val paywallex = Paywallex("pxa_test_…", "https://api.paywallx.xyz", "monapp://paiement")

// 1. Ouvrir la page Paywallex
paywallex.startPayment(activity, amount = "5000", reference = "CMD-1042", title = "Commande 1042")

// 2. Dans l'activité de retour (onNewIntent), ou dans onResume avec null si l'onglet a été fermé
val result = paywallex.handleReturn("CMD-1042", intent.data)
if (result.outcome == PaywallexOutcome.PAID) { /* afficher « Merci » */ }
```

## Abonner une personne

Un abonnement se crée toujours depuis votre serveur avec votre clé secrète (`POST /v1/subscriptions`, avec `success_url` et `cancel_url` égaux à l'adresse de retour de l'app). Votre serveur renvoie `subscribe_url` à l'app, qui l'ouvre avec le kit. Avant d'ouvrir l'accès, votre serveur vérifie l'abonnement (`GET /v1/subscriptions/{id}`).

```kotlin
paywallex.openSubscription(activity, subscribeUrl)
// dans l'activité de retour :
val r = paywallex.handleSubscriptionReturn(intent.data)
if (r.outcome == PaywallexSubscribeOutcome.RETURNED) { /* envoyez r.subscriptionId à votre serveur */ }
```

« Gérer mon abonnement » : `openSubscription(activity, manageUrl)` avec le lien de `POST /v1/subscriptions/{id}/manage_link`.

## Licence

MIT.
