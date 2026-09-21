/**
 * 项目 POM 的声明级盘点：dependency、BOM/managed dependency、exclusion 分开。
 * 不冒充 Maven effective-pom/解析后的传递依赖图；不把注释或 exclusion 算成直接依赖。
 */
export function pomDependencyDeclarations(xml) {
  const source = xml.replace(/<!--[\s\S]*?-->/g, '')
  const tag = (body, name) => body.match(new RegExp(`<${name}\\b[^>]*>\\s*([^<]*?)\\s*</${name}\\s*>`))?.[1]?.trim() ?? null
  const parse = (body, managed) => [...body.matchAll(/<dependency\b[^>]*>([\s\S]*?)<\/dependency\s*>/g)].map(([, block]) => {
    const exclusions = [...block.matchAll(/<exclusion\b[^>]*>([\s\S]*?)<\/exclusion\s*>/g)].map(([, item]) => ({
      groupId: tag(item, 'groupId'),
      artifactId: tag(item, 'artifactId'),
    }))
    const dependency = block.replace(/<exclusions\b[^>]*>[\s\S]*?<\/exclusions\s*>/g, '')
    return {
      groupId: tag(dependency, 'groupId'),
      artifactId: tag(dependency, 'artifactId'),
      version: tag(dependency, 'version'),
      scope: tag(dependency, 'scope'),
      type: tag(dependency, 'type'),
      managed,
      exclusions,
    }
  })
  const management = [...source.matchAll(/<dependencyManagement\b[^>]*>([\s\S]*?)<\/dependencyManagement\s*>/g)]
  const direct = source.replace(/<dependencyManagement\b[^>]*>[\s\S]*?<\/dependencyManagement\s*>/g, '')
  return [...management.flatMap(([, block]) => parse(block, true)), ...parse(direct, false)]
}
