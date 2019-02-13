package org.entcore.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResourceUtils {

    private static final Pattern workspaceDoc = Pattern.compile("/workspace/document/(?<id>((\\d|[a-z]){8}-(\\d|[a-z]){4}-(\\d|[a-z]){4}-(\\d|[a-z]){4}-(\\d|[a-z]){12}))");

    public static List<String> extractIds(String resource) {
        Matcher matcher = workspaceDoc.matcher(resource);
        List<String> documentsIds = new ArrayList<>();
        while (matcher.find()) {
            documentsIds.add(matcher.group("id"));
        }
        return documentsIds;
    }

}
