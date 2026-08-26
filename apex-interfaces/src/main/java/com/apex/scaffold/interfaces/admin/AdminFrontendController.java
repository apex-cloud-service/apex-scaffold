package com.apex.scaffold.interfaces.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.result.view.RedirectView;

/**
 * Same-origin entry point for the embedded administration workbench.
 *
 * <p>将短管理地址（{@code /admin}、{@code /admin/}）重定向到不可变静态入口文档，
 * 使浏览器地址栏保持规范形式。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Controller
public class AdminFrontendController {

    /** Redirect the short administration URL to the immutable static entry document. */
    @GetMapping({"/admin", "/admin/"})
    public RedirectView index() {
        return new RedirectView("/admin/index.html");
    }
}
