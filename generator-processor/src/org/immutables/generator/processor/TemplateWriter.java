/*
    Copyright 2014 Ievgen Lukash

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.immutables.generator.processor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.lang.model.element.TypeElement;
import org.immutables.generator.Intrinsics;
import org.immutables.generator.Templates;
import org.immutables.generator.processor.ImmutableTrees.ApplyExpression;
import org.immutables.generator.processor.ImmutableTrees.AssignGenerator;
import org.immutables.generator.processor.ImmutableTrees.Block;
import org.immutables.generator.processor.ImmutableTrees.BoundAccessExpression;
import org.immutables.generator.processor.ImmutableTrees.Comment;
import org.immutables.generator.processor.ImmutableTrees.ConditionalBlock;
import org.immutables.generator.processor.ImmutableTrees.ForStatement;
import org.immutables.generator.processor.ImmutableTrees.Identifier;
import org.immutables.generator.processor.ImmutableTrees.IfStatement;
import org.immutables.generator.processor.ImmutableTrees.InvokableDeclaration;
import org.immutables.generator.processor.ImmutableTrees.InvokeStatement;
import org.immutables.generator.processor.ImmutableTrees.InvokeString;
import org.immutables.generator.processor.ImmutableTrees.IterationGenerator;
import org.immutables.generator.processor.ImmutableTrees.LetStatement;
import org.immutables.generator.processor.ImmutableTrees.ResolvedType;
import org.immutables.generator.processor.ImmutableTrees.StringLiteral;
import org.immutables.generator.processor.ImmutableTrees.Template;
import org.immutables.generator.processor.ImmutableTrees.TextLine;
import org.immutables.generator.processor.ImmutableTrees.Unit;
import org.immutables.generator.processor.ImmutableTrees.ValueDeclaration;
import org.immutables.generator.processor.Trees.Expression;
import static org.immutables.generator.StringLiterals.*;

/**
 * This part is written with simples possible writer in mind. It was decided not to use dependencies
 * like. Its is possible that in future it will be replaced with self bootstraping, i.e. template
 * generator will be generated by the same framework which generates templates.
 */
public final class TemplateWriter extends TreesTransformer<TemplateWriter.Context> {
  private final TypeElement sourceElement;
  private final String simpleName;
  private final SwissArmyKnife knife;

  public TemplateWriter(SwissArmyKnife knife, TypeElement sourceElement, String simpleName) {
    this.knife = knife;
    this.sourceElement = sourceElement;
    this.simpleName = simpleName;
  }

  public CharSequence toCharSequence(Unit unit) {
    Context context = new Context();
    transform(context, unit);
    return context.builder;
  }

  @Override
  public Unit transform(Context context, Unit value) {
    context.out("package ", knife.elements.getPackageOf(sourceElement).getQualifiedName(), ";")
        .ln().ln()
        .out("import static ", Intrinsics.class, ".*;")
        .ln().ln();

    context
        .out("@", SuppressWarnings.class, "({", toLiteral("all"), "})")
        .ln()
        .out("public class ", simpleName, " extends ", sourceElement.getQualifiedName())
        .out(" {")
        .ln();

    Unit unit = super.transform(context, value);

    context
        .ln()
        .out('}')
        .ln();

    return unit;
  }

//  /** Overriden to specify order in which we process declaration first, and then parts. */
//  @Override
//  public Template transform(Context scope, Template template) {
//
//  }
//
//  /** Overriden to specify order in which we process declaration first, and then parts. */
//  @Override
//  public LetStatement transform(Context scope, LetStatement statement) {
//    return statement
//        .withDeclaration(transformLetStatementDeclaration(scope, statement, statement.declaration()))
//        .withParts(transformLetStatementListParts(scope, statement, statement.parts()));
//  }
//
//  /** Overriden to specify order in which we process declaration first, and then parts. */
//  @Override
//  public ForStatement transform(Context scope, ForStatement statement) {
//    return statement
//        .withDeclaration(transformForStatementListDeclaration(scope, statement, statement.declaration()))
//        .withParts(transformForStatementListParts(scope, statement, statement.parts()));
//  }
  @Override
  public Template transform(final Context context, final Template template) {
    String name = template.declaration().name().value();

    context.ln()
        .out("public ")
        .out(Templates.Invokable.class)
        .out(" ")
        .out(name)
        .out("() { return ")
        .out(name)
        .out("; }").ln();

    context.out("private ");

    new TemplateLike() {
      {
        declaration = template.declaration();
        variable = true;
      }

      @Override
      void body() {
        transformTemplateDeclaration(context, template, template.declaration());
        transformTemplateListParts(context, template, template.parts());
      }
    }.generate(context);

    context.out(";").ln();

    return template;
  }

  abstract class TemplateLike {
    boolean variable;
    boolean capture;
    Trees.InvokableDeclaration declaration;

    final void generate(Context context) {
      if (variable) {
        context.out("final ")
            .out(Templates.Invokable.class)
            .out(" ")
            .out(declaration.name().value())
            .out(" = ");
      }

      context.out("new ").out(Templates.Fragment.class)
          .out("(", declaration.parameters().size())
          .out(capture ? ", __" : "")
          .out(") ")
          .openBrace()
          .ln()
          .out("@Override public void run(").out(Templates.Invokation.class).out(" __) ")
          .openBrace()
          .indent()
          .ln();

      int braces = context.getAndSetPendingBraces(0);
      context.delimit();

      body();

      context.delimit();

      context.getAndSetPendingBraces(braces);
      context.outdent().ln().closeBraces();
    }

    abstract void body();
  }

  @Override
  public LetStatement transform(final Context context, final LetStatement statement) {
    new TemplateLike() {
      {
        declaration = statement.declaration();
        variable = true;
        capture = true;
      }

      @Override
      void body() {
        context.out("final ")
            .out(Templates.Invokable.class)
            .out(" ")
            .out(statement.declaration().name().value())
            .out(" = this;")
            .ln();

        transformLetStatementDeclaration(context, statement, statement.declaration());
        transformLetStatementListParts(context, statement, statement.parts());
      }
    }.generate(context);

    context.out(";").delimit();

    return statement;
  }

  @Override
  public ForStatement transform(Context context, ForStatement statement) {
    context.openBrace();
    context.infor()
        .out("final ")
        .out(Templates.Iteration.class)
        .out(" ")
        .out(context.accessMapper(TypeResolver.ITERATION_ACCESS_VARIABLE))
        .out(" = new ")
        .out(Templates.Iteration.class)
        .out("();")
        .ln();

    transformForStatementListDeclaration(context, statement, statement.declaration());

    int braces = context.getAndSetPendingBraces(0);
    context.indent();

    context.delimit();
    transformForStatementListParts(context, statement, statement.parts());
    context.delimit();

    context.out(context.accessMapper(TypeResolver.ITERATION_ACCESS_VARIABLE)).out(".index++;").ln();
    context.out(context.accessMapper(TypeResolver.ITERATION_ACCESS_VARIABLE)).out(".first = false;");

    context.getAndSetPendingBraces(braces);
    context.outfor().outdent().ln()
        .closeBraces().ln().delimit();

    return statement;
  }

  @Override
  public InvokeString transform(Context context, InvokeString value) {
    context.out("$(__, ", value.literal(), ");").ln();
    return value;
  }

  @Override
  public InvokeStatement transform(final Context context, final InvokeStatement statement) {
    context.out("$(__, ");
    transformInvokeStatementAccess(context, statement, statement.access());
    transformInvokeStatementListParams(context, statement, statement.params());

    if (!statement.parts().isEmpty()) {
      context.out(", ");

      new TemplateLike() {
        {
          declaration = InvokableDeclaration.builder()
              .name(Identifier.of(""))
              .build();

          capture = true;
        }

        @Override
        void body() {
          transformInvokeStatementListParts(context, statement, statement.parts());
        }
      }.generate(context);
    }

    context.out(");").ln();

    return statement;
  }

  @Override
  protected Iterable<Expression> transformInvokeStatementListParams(
      Context context,
      InvokeStatement value,
      List<Expression> collection) {
    for (Trees.Expression element : collection) {
      context.out(", ");
      transformInvokeStatementParams(context, value, element);
    }
    return collection;
  }

  @Override
  public AssignGenerator transform(Context context, AssignGenerator generator) {
    transformAssignGeneratorDeclaration(context, generator, generator.declaration());
    context.out(" = (")
        .out(requiredResolvedTypeOfDeclaration(generator.declaration()))
        .out(") $(");
    transformAssignGeneratorFrom(context, generator, generator.from());
    context.out(");").ln();
    return generator;
  }

  @Override
  public IterationGenerator transform(Context context, IterationGenerator generator) {
    context.out("for (");
    transformIterationGeneratorDeclaration(context, generator, generator.declaration());
    context.out(" : $in(");
    transformIterationGeneratorFrom(context, generator, generator.from());
    context.out(")) ").openBrace().ln();

    if (generator.condition().isPresent()) {
      context.out("if ($if(");
      transformIterationGeneratorOptionalCondition(context, generator, generator.condition());
      context.out(")) ").openBrace().ln();
    }

    return generator;
  }

  @Override
  public ValueDeclaration transform(
      Context context,
      ValueDeclaration value) {
    context.out("final ").out(requiredResolvedTypeOfDeclaration(value)).out(" ").out(value.name().value());
    return value;
  }

  private Object requiredResolvedTypeOfDeclaration(Trees.ValueDeclaration value) {
    return ((ResolvedType) value.type().get()).type();
  }

  @Override
  public TextLine transform(Context context, TextLine line) {
    context.out("__.out(")
        .out(line.fragment())
        .out(line.newline() ? ").ln();" : ");").ln();
    return line;
  }

  @Override
  public StringLiteral transform(Context context, StringLiteral value) {
    context.out(value);
    return value;
  }

  @Override
  public BoundAccessExpression transform(Context context, BoundAccessExpression value) {
    ImmutableList<Accessors.BoundAccess> accessList = TypeResolver.asBoundAccess(value.accessor());

    StringBuilder expressionBuilder = new StringBuilder();

    for (int i = 0; i < accessList.size(); i++) {
      boolean first = i == 0;
      boolean last = i != accessList.size() - 1;

      Accessors.BoundAccess access = accessList.get(i);

      if (!first) {
        expressionBuilder.append(".");
      }

      String name = access.name;

      if (first) {
        name = context.accessMapper(name);
      }

      expressionBuilder.append(name).append(access.callable ? "()" : "");

      if (access.boxed && last) {
        expressionBuilder.insert(0, "$(");
        expressionBuilder.append(")");
      }
    }

    context.out(expressionBuilder);

    return value;
  }

  @Override
  public ApplyExpression transform(Context context, ApplyExpression value) {
    context.out("$(");
    ApplyExpression expression = super.transform(context, value);
    context.out(")");
    return expression;
  }

  @Override
  protected Iterable<Expression> transformApplyExpressionListParams(
      Context context,
      ApplyExpression value,
      List<Expression> collection) {
    boolean first = true;
    for (Trees.Expression element : collection) {
      if (!first) {
        context.out(", ");
      }
      first = false;
      transformApplyExpressionParams(context, value, element);
    }
    return collection;
  }

  private void writeConditionPart(Context context, ConditionalBlock block) {
    context.out("if ($if(");

    transformConditionalBlockCondition(context, block, block.condition());

    context.out(")) {")
        .indent()
        .ln();

    context.delimit();
    transformConditionalBlockListParts(context, block, block.parts());
  }

  @Override
  public IfStatement transform(Context context, IfStatement statement) {
    context.delimit();
    writeConditionPart(context, (ConditionalBlock) statement.then());

    for (Trees.ConditionalBlock block : statement.otherwiseIf()) {
      context.outdent().out("} else ");

      writeConditionPart(context, (ConditionalBlock) block);
    }

    if (statement.otherwise().isPresent()) {
      context.outdent()
          .ln()
          .out("} else {")
          .indent()
          .ln()
          .delimit();

      transform(context, (Block) statement.otherwise().get());
    }

    context.outdent()
        .ln()
        .out("}")
        .ln()
        .delimit();

    return statement;
  }

  @Override
  public Comment transform(Context context, Comment value) {
    context.delimit();
    return value;
  }

  @Override
  public InvokableDeclaration transform(Context context, InvokableDeclaration value) {
    int count = 0;

    for (Trees.Parameter parameter : value.parameters()) {
      int paramIndex = count++;
      String typeName = parameter.type().toString();
      context.out("final ", typeName, " ", parameter.name().value()).out(" = ");
      if (typeName.equals(String.class.getName())) {
        context.out("__.param(", paramIndex, ").toString();").ln();
      } else if (typeName.equals(Boolean.class.getName())) {
        context.out("$if(__.param(", paramIndex, "));").ln();
      } else if (typeName.equals(Object.class.getName())) {
        context.out("__.param(", paramIndex, ");").ln();
      } else {
        context.out("$cast(__.param(", paramIndex, "));").ln();
      }
    }

    return super.transform(context, value);
  }

  static class Context {
    final StringBuilder builder = new StringBuilder();
    private int indentLevel;
    private int bracesToClose;
    private int forLevels;

    Context infor() {
      forLevels++;
      return this;
    }

    public Context delimit() {
      out("__.delimit();");
      return this;
    }

    Context outfor() {
      forLevels--;
      return this;
    }

    Context indent() {
      indentLevel++;
      return this;
    }

    Context outdent() {
      indentLevel--;
      return this;
    }

    Context out(Object... objects) {
      for (Object object : objects) {
        out(object);
      }
      return this;
    }

    public String accessMapper(String identifer) {
      if (TypeResolver.ITERATION_ACCESS_VARIABLE.equals(identifer)) {
        return "_it" + forLevels;
      }
      return identifer;
    }

    int getAndSetPendingBraces(int bracesToClose) {
      int value = this.bracesToClose;
      this.bracesToClose = bracesToClose;
      return value;
    }

    Context closeBraces() {
      for (int i = 0; i < bracesToClose; i++) {
        builder.append('}');
      }
      bracesToClose = 0;
      return this;
    }

    Context openBrace() {
      builder.append('{');
      bracesToClose++;
      return this;
    }

    Context out(Object object) {
      if (object instanceof Optional<?>) {
        object = ((Optional<?>) object).orNull();
      }
      if (object instanceof Class<?>) {
        object = ((Class<?>) object).getCanonicalName();
      }
      if (object instanceof CharSequence) {
        builder.append((CharSequence) object);
        return this;
      }
      builder.append(String.valueOf(object));
      return this;
    }

    Context ln() {
      builder.append('\n');
      for (int i = 0; i < indentLevel; i++) {
        builder.append("  ");
      }
      return this;
    }
  }
}
