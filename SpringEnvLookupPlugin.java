package com.example.logging;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

@Plugin(name = "spring", category = "Lookup")
public class SpringEnvLookupPlugin extends SpringEnvLookup implements StrLookup {
}
