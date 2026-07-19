package com.ebremer.halcyon.mcp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.springframework.ai.tool.annotation.Tool;

/**
 * The first (and deliberately only) MCP tool surface of the skeleton:
 * identification. It reads static constants — no settings file, no dataset,
 * no network — so it discloses nothing a page footer would not.
 *
 * <p>Every tool that touches actual Halcyon data (LWS listings, SPARQL,
 * imagery) is specified in {@code TODO.md} and is gated there on the P0
 * authentication work: tools must act as ordinary clients carrying the
 * <em>caller's</em> identity — the same no-privileged-path rule the storage
 * UIs follow — and none of that may ship before {@code /mcp} authenticates.
 */
public class HalcyonInfoTools {

    @Tool(name = "halcyon_version",
            description = "The Halcyon server software name and version.")
    public String version() {
        return HalcyonSettings.HALCYONSOFTWARE;
    }
}
