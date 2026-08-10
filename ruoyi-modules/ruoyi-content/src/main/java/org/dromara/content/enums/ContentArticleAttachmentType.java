package org.dromara.content.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文章附件类型。
 */
@Getter
@RequiredArgsConstructor
public enum ContentArticleAttachmentType {

    IMAGE("0"),
    VIDEO("1");

    private final String code;
}
