package ${domainPackage}.model.entity;

<#if stateMachineEnabled>import com.apex.scaffold.common.state.StateTransitionMachine;</#if>
import java.time.LocalDateTime;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * ${tableComment}聚合根实体。
 *
 * <p>表示系统中的${tableComment}定义，承载${tableComment}的核心业务属性与行为。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>${tableComment}创建后通过工厂方法初始化时间戳与逻辑删除标记</li>
 *   <li>更新核心信息与状态转换通过领域行为方法完成，自动刷新更新时间</li>
 * </ul>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ${entityName}Entity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

<#if stateMachineEnabled>
    private static final StateTransitionMachine<String> STATE_MACHINE =
            StateTransitionMachine.<String>builder()
<#list stateTransitions as transition>
                    .allow("${transition.from}", "${transition.to}")
</#list>
                    .build();

</#if>
<#if tenantAware>
    private String tenantId;

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

</#if>
<#list fields as field>
    /**
     * ${field.comment}。
     */
    private ${field.type} ${field.name};

    /** Sets ${field.name}. For persistence/construction use only. */
    public void set${field.name?cap_first}(${field.type} value) {
        this.${field.name} = value;
    }

</#list>
    /**
     * 默认构造函数。
     */
    public ${entityName}Entity() {
        super();
    }

    /**
     * 静态工厂方法：创建${tableComment}。
     *
<#list createFields as field>
     * @param ${field.name} ${field.comment}
</#list>
     * @return 新创建的${tableComment}实体
     */
    public static ${entityName}Entity create(
<#list createFields as field>
            ${field.type} ${field.name}<#if field?has_next>,</#if>
</#list>) {
        ${entityName}Entity entity = new ${entityName}Entity();
<#list createFields as field>
        entity.${field.name} = ${field.name};
</#list>
<#if stateMachineEnabled>
        entity.${stateField} = "${initialState}";
</#if>
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：更新${tableComment}核心信息。
     *
<#list updateFields as field>
     * @param ${field.name} ${field.comment}
</#list>
     */
    public void updateInfo(
<#list updateFields as field>
            ${field.type} ${field.name}<#if field?has_next>,</#if>
</#list>) {
<#list updateFields as field>
        this.${field.name} = ${field.name};
</#list>
        touch();
    }
<#if stateMachineEnabled>

    public void transitionTo(String targetState) {
        this.${stateField} = STATE_MACHINE.transition(this.${stateField}, targetState);
        touch();
    }
</#if>
}
