package com.fastingshift.domain.timeline

import java.time.Duration

/** Evidence labels (PRD F9, SR-7). */
enum class EvidenceLabel(val display: String) {
    ESTABLISHED("Established"),
    EMERGING("Emerging"),
    ANIMAL_ONLY("Animal-only"),
    UNKNOWN("Unknown"),
}

data class TimelineClaim(val text: String, val label: EvidenceLabel, val source: String)

data class TimelineStage(val fromHours: Int, val title: String, val claims: List<TimelineClaim>)

object BodyTimeline {
    /** Bump when content changes so the app can show "updated" and tests can pin it. */
    const val CONTENT_VERSION = 1

    /**
     * DRAFT CONTENT for Robert to rewrite in his voice and for clinician review. Timings are
     * approximate and vary between people; the copy says so on screen.
     */
    val stages: List<TimelineStage> = listOf(
        TimelineStage(
            0, "Digesting your last meal",
            listOf(
                TimelineClaim(
                    "Blood sugar and insulin rise after eating, then fall back over the next few hours.",
                    EvidenceLabel.ESTABLISHED,
                    "Standard human physiology (e.g. Guyton & Hall Textbook of Medical Physiology, metabolism chapters).",
                ),
            ),
        ),
        TimelineStage(
            12, "Running on stored glycogen",
            listOf(
                TimelineClaim(
                    "Your liver releases stored glycogen to keep blood sugar steady; fat use starts to climb.",
                    EvidenceLabel.ESTABLISHED,
                    "Cahill GF Jr. Fuel metabolism in starvation. Annu Rev Nutr. 2006;26:1-22.",
                ),
            ),
        ),
        TimelineStage(
            18, "Ketones begin to rise",
            listOf(
                TimelineClaim(
                    "As glycogen falls, the liver makes more ketones from fat. Levels vary a lot between people.",
                    EvidenceLabel.ESTABLISHED,
                    "Cahill GF Jr. Annu Rev Nutr. 2006;26:1-22; Anton SD et al. Obesity. 2018;26(2):254-268.",
                ),
            ),
        ),
        TimelineStage(
            24, "Deeper into fat burning",
            listOf(
                TimelineClaim(
                    "Liver glycogen is largely used up for many people around this point, so fat and ketones supply more energy.",
                    EvidenceLabel.ESTABLISHED,
                    "Rothman DL et al. Science. 1991;254(5031):573-576.",
                ),
                TimelineClaim(
                    "Autophagy (cellular clean-up) increases with fasting in animals. When, or how much, it happens in humans is not known.",
                    EvidenceLabel.UNKNOWN,
                    "Alirezaei M et al. Autophagy. 2010;6(6):702-710 (mice); human timing not established.",
                ),
            ),
        ),
        TimelineStage(
            48, "Extended fast",
            listOf(
                TimelineClaim(
                    "Growth hormone pulses rise during multi-day fasts in human studies.",
                    EvidenceLabel.EMERGING,
                    "Ho KY et al. J Clin Invest. 1988;81(4):968-975.",
                ),
                TimelineClaim(
                    "Claims of immune-system 'regeneration' come from mouse studies and have not been shown in people.",
                    EvidenceLabel.ANIMAL_ONLY,
                    "Cheng CW et al. Cell Stem Cell. 2014;14(6):810-823 (mice).",
                ),
            ),
        ),
        TimelineStage(
            72, "Very long fast",
            listOf(
                TimelineClaim(
                    "Risks such as electrolyte problems rise with length; refeeding needs care after long fasts.",
                    EvidenceLabel.ESTABLISHED,
                    "NICE CG32 (2006, updated 2017): Nutrition support for adults, refeeding risk guidance.",
                ),
            ),
        ),
    )

    /** The stage the user is in at [elapsed], or null before the first stage. */
    fun stageAt(elapsed: Duration): TimelineStage? = stages.lastOrNull { elapsed >= Duration.ofHours(it.fromHours.toLong()) }

    fun nextStage(elapsed: Duration): TimelineStage? = stages.firstOrNull { elapsed < Duration.ofHours(it.fromHours.toLong()) }
}
