package com.avatarsdev.freellmapi.core.gateway

import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns the `model` string of a request into an ordered list of (provider, model) candidates.
 *
 * Accepted forms:
 *  - `auto` (or empty): the `auto` route if defined, otherwise every usable provider's default model by priority.
 *  - a route alias you created (e.g. `fast`): its targets in order.
 *  - an exact model id (e.g. `llama-3.3-70b-versatile`): every provider that has it enabled.
 *  - `<provider-slug>/<model>` (e.g. `groq/openai/gpt-oss-120b`): that provider only.
 * When cross-model fallback is on and the requested model is known, the default chain is appended so another model can
 * answer if every provider for it fails. Unknown models resolve to nothing (HTTP 404).
 */
class ModelRouter {
    private val roundRobin = AtomicInteger(0)

    fun resolve(requestedModel: String, snap: GatewaySnapshot): List<Candidate> {
        val active = snap.providers.filter { it.isUsable }.sortedWith(compareBy({ it.priority }, { it.id }))
        val byId = active.associateBy { it.id }
        val requested = requestedModel.trim()

        fun defaultChain(): List<Candidate> = active.mapNotNull { p ->
            val model = p.defaultModel?.takeIf { it.isNotBlank() } ?: p.models.firstOrNull()
            model?.let { Candidate(p, it) }
        }

        fun routeTargets(route: RouteSnapshot): List<Candidate> =
            route.targets.mapNotNull { t -> byId[t.providerId]?.let { Candidate(it, t.modelId) } }

        val isAuto = requested.isEmpty() || requested.equals("auto", ignoreCase = true)
        val route = snap.routes.firstOrNull { it.alias.equals(if (isAuto) "auto" else requested, ignoreCase = true) }

        val primary: List<Candidate> = when {
            route != null -> routeTargets(route)
            isAuto -> defaultChain()
            else -> {
                val exact = active.filter { requested in it.models }.map { Candidate(it, requested) }
                if (exact.isNotEmpty()) {
                    exact
                } else {
                    val slash = requested.indexOf('/')
                    if (slash > 0) {
                        val prefix = requested.substring(0, slash)
                        val rest = requested.substring(slash + 1)
                        active.filter { it.slug.equals(prefix, true) || it.type.id.equals(prefix, true) }
                            .filter { rest.isNotBlank() }
                            .map { Candidate(it, rest) }
                    } else {
                        emptyList()
                    }
                }
            }
        }

        // An unknown model is an error (404), never silently answered by some other model.
        if (primary.isEmpty() && !isAuto && route == null) return emptyList()

        val ordered = order(primary, snap)
        val all = if (snap.config.fallbackEnabled && snap.config.crossModelFallback) ordered + order(defaultChain(), snap) else ordered
        return all.distinctBy { it.key }
    }

    private fun order(list: List<Candidate>, snap: GatewaySnapshot): List<Candidate> {
        if (list.size < 2) return list
        return when (snap.config.strategy) {
            RoutingStrategy.PRIORITY -> list
            RoutingStrategy.ROUND_ROBIN -> {
                val start = Math.floorMod(roundRobin.getAndIncrement(), list.size)
                list.drop(start) + list.take(start)
            }
            RoutingStrategy.FASTEST -> list.sortedBy { snap.latency[it.key] ?: Long.MAX_VALUE }
        }
    }
}
