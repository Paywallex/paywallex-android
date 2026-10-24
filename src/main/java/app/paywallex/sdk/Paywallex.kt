// Paywallex pour Android (v1.0.0, DV4) : paiement, abonnement, retour dans l'app, confirmation serveur. Ouvre la page de paiement dans un onglet personnalisé du navigateur (Custom Tabs),
// qui partage la connexion Paywallex de Chrome : la personne paie avec son compte en un geste (App Switch),
// puis revient dans votre app par votre schéma (monapp://…), déclaré dans le manifeste de l'activité de retour.
// Le résultat est toujours confirmé auprès de Paywallex (/connect/status) : l'adresse de retour seule ne prouve rien.
package app.paywallex.sdk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class PaywallexOutcome { PAID, PENDING, FAILED, EXPIRED, CANCELLED }

data class PaywallexResult(
    val outcome: PaywallexOutcome,
    val reference: String,
    val amountMinor: String?,
    val currency: String?,
    val paymentId: String?,
)

enum class PaywallexSubscribeOutcome { RETURNED, CANCELLED }

/** subscriptionId : votre serveur le vérifie (GET /v1/subscriptions/{id}) avant d'ouvrir l'accès. */
data class PaywallexSubscribeResult(val outcome: PaywallexSubscribeOutcome, val subscriptionId: String?)

class PaywallexException(val code: String, message: String) : IOException(message)

/**
 * @param appKey clé publique de l'app connectée (pxa_test_… en bac à sable, pxa_live_… en production).
 * @param baseUrl adresse de l'API Paywallex, sans « /api » (donnée dans le Labs).
 * @param returnUrl adresse de retour déclarée dans la fiche de l'app, ex. « monapp://paiement ».
 */
class Paywallex(private val appKey: String, baseUrl: String, private val returnUrl: String) {
    private val base = baseUrl.trimEnd('/')

    init {
        val scheme = Uri.parse(returnUrl).scheme?.lowercase()
        require(scheme != null && scheme != "http" && scheme != "https") {
            "returnUrl doit utiliser le schéma de votre app (ex. monapp://paiement)."
        }
    }

    /** Étape 1 : crée le paiement et ouvre la page Paywallex. Gardez la référence pour l'étape 2. */
    suspend fun startPayment(activity: Activity, amount: String, reference: String, title: String? = null) {
        val body = JSONObject().put("app", appKey).put("amount", amount).put("ref", reference).put("return", returnUrl)
        if (title != null) body.put("title", title)
        val created = request("POST", "$base/api/connect/pay", body.toString())
        val url = created.optString("url").ifEmpty { throw PaywallexException("invalid_response", "Réponse Paywallex inattendue.") }
        withContext(Dispatchers.Main) {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, Uri.parse(url))
        }
    }

    /**
     * Étape 2 : à appeler dans l'activité de retour (onCreate / onNewIntent) avec intent.data,
     * ou dans onResume avec null si la personne a fermé l'onglet sans payer.
     */
    suspend fun handleReturn(reference: String, data: Uri?): PaywallexResult {
        val hint = data?.takeIf { it.toString().startsWith(returnUrl.substringBefore("://") + "://") }
            ?.getQueryParameter("paywallex_status") ?: "cancelled"
        return status(reference, hint)
    }

    /**
     * Abonnement, étape 1 : ouvre le lien créé par VOTRE SERVEUR (POST /v1/subscriptions avec success_url et
     * cancel_url = returnUrl). Sert aussi pour « Gérer mon abonnement » (lien de POST /v1/subscriptions/{id}/manage_link).
     */
    fun openSubscription(activity: Activity, subscribeUrl: String) {
        val u = Uri.parse(subscribeUrl)
        if (u.scheme?.lowercase() != "https") throw PaywallexException("invalid_url", "Le lien doit être une adresse Paywallex en https.")
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, u)
    }

    /** Abonnement, étape 2 : dans l'activité de retour, avec intent.data (ou null si l'onglet a été fermé). */
    fun handleSubscriptionReturn(data: Uri?): PaywallexSubscribeResult {
        val id = data?.takeIf { it.toString().startsWith(returnUrl.substringBefore("://") + "://") }
            ?.getQueryParameter("subscription_id")?.ifEmpty { null }
        return if (id != null) PaywallexSubscribeResult(PaywallexSubscribeOutcome.RETURNED, id)
        else PaywallexSubscribeResult(PaywallexSubscribeOutcome.CANCELLED, null)
    }

    /** Confirme l'état d'un paiement auprès de Paywallex (à refaire côté serveur avant de livrer). */
    suspend fun status(reference: String, hint: String? = null): PaywallexResult {
        val q = "app=" + URLEncoder.encode(appKey, "UTF-8") + "&ref=" + URLEncoder.encode(reference, "UTF-8")
        val j = request("GET", "$base/api/connect/status?$q", null)
        val outcome = when (j.optString("status")) {
            "paid" -> PaywallexOutcome.PAID
            "expired" -> PaywallexOutcome.EXPIRED
            else -> when (hint) { "cancelled" -> PaywallexOutcome.CANCELLED; "failed" -> PaywallexOutcome.FAILED; else -> PaywallexOutcome.PENDING }
        }
        fun s(k: String) = if (j.isNull(k)) null else j.optString(k).ifEmpty { null }
        return PaywallexResult(outcome, reference, s("amountMinor"), s("currency"), s("paymentId"))
    }

    private suspend fun request(method: String, url: String, body: String?): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 15000
            c.setRequestProperty("Paywallex-Sdk", "android/1.0.0")
            if (body != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val j = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) throw PaywallexException(j.optString("code", "http_$code"), j.optString("error", "Paiement indisponible."))
            j
        } finally { c.disconnect() }
    }
}
