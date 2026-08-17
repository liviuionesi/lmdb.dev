import { ProjectSchema, ModuleDoc, ClassDoc } from '@/types/schema';
import defaultSchema from '@/data/default-schema.json';

export function getProjectSchema(): ProjectSchema {
  return defaultSchema as ProjectSchema;
}

export function getAllModules(): ModuleDoc[] {
  return getProjectSchema().modules;
}

export function getModuleById(moduleId: string): ModuleDoc | undefined {
  return getAllModules().find((m) => m.id === moduleId);
}

export function getClassById(moduleId: string, className: string): { module: ModuleDoc; classDoc: ClassDoc } | undefined {
  const moduleDoc = getModuleById(moduleId);
  if (!moduleDoc) return undefined;
  const classDoc = moduleDoc.classes.find((c) => c.name === className || c.id === className);
  if (!classDoc) return undefined;
  return { module: moduleDoc, classDoc };
}
