export interface Annotation {
  name: string;
  value: string | null;
}

export interface Parameter {
  name: string;
  type: string;
  description: string;
}

export interface JavadocTags {
  params: { name: string; description: string }[];
  returns: string;
  throws: { exception: string; description: string }[];
  see: string[];
  since: string;
  notes: string[];
  pitfalls: string[];
}

export interface MethodDoc {
  name: string;
  returnType: string;
  parameters: Parameter[];
  throws: string | null;
  annotations: Annotation[];
  isEndpoint: boolean;
  httpMethod: string | null;
  httpPath: string | null;
  summary: string;
  description: string;
  returnDescription: string;
  tags: JavadocTags;
  signature: string;
}

export interface ClassDoc {
  id: string;
  name: string;
  kind: 'class' | 'interface' | 'record' | 'enum';
  package: string;
  category: string;
  filePath: string;
  extends: string | null;
  implements: string | null;
  annotations: Annotation[];
  summary: string;
  description: string;
  tags: JavadocTags;
  methods: MethodDoc[];
  sourceCode: string;
}

export interface ModuleDoc {
  id: string;
  name: string;
  port: string;
  description: string;
  classes: ClassDoc[];
}

export interface ProjectSchema {
  projectName: string;
  version: string;
  description: string;
  generatedAt: string;
  totalClasses: number;
  modules: ModuleDoc[];
}
