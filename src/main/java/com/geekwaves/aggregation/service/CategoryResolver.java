package com.geekwaves.aggregation.service;

import com.geekwaves.aggregation.adapter.FetchedItem;
import com.geekwaves.aggregation.domain.InfoSource;
import org.springframework.stereotype.Component;

@Component
public class CategoryResolver {

    public String categoryOf(InfoSource source, FetchedItem item) {
        String type = source.getType() == null ? "" : source.getType();
        String code = source.getCode() == null ? "" : source.getCode();
        if (type.contains("GITHUB")) {
            if (code.contains("trending")) return "REPO";
            if (item.extraJson() != null && item.extraJson().contains("stars")) return "REPO";
            return "RELEASE";
        }
        return "NEWS";
    }
}
