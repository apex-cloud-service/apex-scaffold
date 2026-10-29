package com.apex.scaffold.domain.gateway;

import com.apex.scaffold.domain.model.moderation.ImageModerationResult;
import com.apex.scaffold.domain.model.moderation.ModerationScene;
import java.util.List;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

/**
 * 图片内容安全审核端口。
 *
 *
 *<p>定义图片内容审核的领域端口，将外部内容审核服务（如百度智能云内容审核、
 *阿里绿网等）的 HTTP 调用细节隔离在基础设施层。应用层仅依赖此端口，
 *不感知具体的审核 API 路径、鉴权方式和响应格式。</p>
 *
 *<h2>设计说明</h2>
 *<ul>
 *  <li>上传照片前检测图片是否包含违规内容（色情、暴力、政治敏感等）</li>
 *  <li>支持按场景灵活组合检测维度，返回聚合后的结构化审核结果</li>
 *  <li>审核结果使用领域类型 {@link ImageModerationResult} 传递，不退化为基础类型的 Map 或字符串</li>
 *</ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ImageModerationPort {

    /**
     * 审核一张图片，并返回所有检测场景聚合后的结果。
     *
     * @param image 待审核的图片资源
     * @param scenes 需要检测的场景列表
     * @return 包含各场景检测结果的聚合审核结果
     */
    Mono<ImageModerationResult> moderate(Resource image, List<ModerationScene> scenes);
}
