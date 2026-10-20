<#-- Shared chronological call emission for Generated/Todo tests. Expects `call` in scope. -->
<#if call.loginHelper!false>
        ${call.pageVar}.login(
<#if call.userPropKey?has_content && (call.userSecret!false)>
            project.utils.reports.Secrets.property("${call.userPropKey?j_string}")
<#elseif call.userPropKey?has_content>
            PropertyReader.getProperty("${call.userPropKey?j_string}")
<#else>
            "${(call.userValue!'')?j_string}"
</#if>
            ,
<#if call.passPropKey?has_content && (call.passSecret!false)>
            project.utils.reports.Secrets.property("${call.passPropKey?j_string}")
<#elseif call.passPropKey?has_content>
            PropertyReader.getProperty("${call.passPropKey?j_string}")
<#else>
            "${(call.passValue!'')?j_string}"
</#if>
        );
<#elseif call.propKey?has_content && (call.secret!false)>
        ${call.pageVar}.${call.method}(project.utils.reports.Secrets.property("${call.propKey?j_string}"));
<#elseif call.propKey?has_content>
        ${call.pageVar}.${call.method}(PropertyReader.getProperty("${call.propKey?j_string}"));
<#elseif call.needsValue>
        ${call.pageVar}.${call.method}("${call.value?j_string}");
<#else>
        ${call.pageVar}.${call.method}();
</#if>
