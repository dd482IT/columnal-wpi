package records.transformations.function;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.typeExp.MutVar;
import records.typeExp.TypeClassRequirements;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * This class is one function, it will have a unique name, a single type and a single implementation
 */
public abstract class FunctionDefinition implements StandardFunctionDefinition
{
    private static final ResourceBundle FUNCTION_MINIS = ResourceBundle.getBundle("function_minis");
    private static final ResourceBundle FUNCTION_TYPEARGS = ResourceBundle.getBundle("function_typeargs");
    private static final ResourceBundle FUNCTION_CONSTRAINTS = ResourceBundle.getBundle("function_constraints");
    private static final ResourceBundle FUNCTION_UNITARGS = ResourceBundle.getBundle("function_unitargs");
    private static final ResourceBundle FUNCTION_TYPES = ResourceBundle.getBundle("function_types");
    private static final ResourceBundle FUNCTION_SYNONYMS = ResourceBundle.getBundle("function_synonyms");
    
    // Namespace colon function name
    private final @FuncDocKey String funcDocKey;
    private final TypeMatcher typeMatcher;
    private final @Localized String miniDescription;
    private final ImmutableList<String> synonyms;
    private final ImmutableList<String> paramNames;

    public FunctionDefinition(@FuncDocKey String funcDocKey) throws InternalException
    {
        this.funcDocKey = funcDocKey;
        Details details = lookupFunction(extractName(funcDocKey), funcDocKey);
        this.miniDescription = details.miniDescription;
        this.typeMatcher = details.typeMatcher;
        this.synonyms = details.synonyms;
        this.paramNames = details.paramNames;
    }

    private static String extractNamespace(@FuncDocKey String funcDocKey)
    {
        String[] split = funcDocKey.split(":");
        return split[0];
    }

    @SuppressWarnings("identifier")
    private static @ExpressionIdentifier String extractName(@FuncDocKey String funcDocKey)
    {
        String[] split = funcDocKey.split(":");
        return split[split.length - 1];
    }

    public Object getScopedName()
    {
        return funcDocKey;
    }

    private static class Details
    {
        private final @Localized String miniDescription;
        private final ImmutableList<String> synonyms;
        private final ImmutableList<String> paramNames;
        private final TypeMatcher typeMatcher;

        private Details(@Localized String miniDescription, ImmutableList<String> synonyms, ImmutableList<String> paramNames, TypeMatcher typeMatcher)
        {
            this.miniDescription = miniDescription;
            this.synonyms = synonyms;
            this.paramNames = paramNames;
            this.typeMatcher = typeMatcher;
        }
    }
    
    private static Details lookupFunction(String functionName, @FuncDocKey String funcDocKey) throws InternalException
    {
        // We call ResourceBundle.getString() here, but it's covered by @FuncDocKey rather than @LocalizableKey,
        // especially since the keys occur duplicated in each file.
        @SuppressWarnings("all")
        @LocalizableKey String key = funcDocKey;
        try
        {
            // TODO move the signature to its own file?
            @SuppressWarnings("all")
            @LocalizableKey String sigKey = key + ":sig";
            return new Details(
                FUNCTION_MINIS.getString(key),
                ImmutableList.copyOf(StringUtils.split(FUNCTION_SYNONYMS.getString(key), ";")),
                ImmutableList.copyOf(StringUtils.split(FUNCTION_TYPES.getString(sigKey), ";")),
                parseFunctionType(functionName,
                    Arrays.asList(StringUtils.split(FUNCTION_TYPEARGS.getString(key), ";")),
                    Arrays.asList(StringUtils.split(FUNCTION_CONSTRAINTS.getString(key), ";")),
                    Arrays.asList(StringUtils.split(FUNCTION_UNITARGS.getString(key), ";")),
                    FUNCTION_TYPES.getString(key)
                )
            );
        }
        catch (MissingResourceException e)
        {
            throw new InternalException("Missing information for " + key, e);
        }
    }

    private static TypeMatcher parseFunctionType(String functionName, List<String> typeArgs, List<String> constraints, List<String> unitArgs, String functionType)
    {
        return typeManager -> {
            try
            {
                Map<String, Either<MutUnitVar, MutVar>> typeVars = new HashMap<>();
                for (String typeArg : typeArgs)
                {
                    TypeClassRequirements typeClassRequirements = TypeClassRequirements.empty();
                    for (String constraint : constraints)
                    {
                        if (constraint.endsWith(" " + typeArg));
                        {
                            typeClassRequirements = TypeClassRequirements.union(typeClassRequirements,
                                TypeClassRequirements.require(StringUtils.removeEnd(constraint, " " + typeArg), functionName));
                        }
                    }

                    typeVars.put(typeArg, Either.right(new MutVar(null)));
                }
                for (String unitArg : unitArgs)
                {
                    typeVars.put(unitArg, Either.left(new MutUnitVar()));
                }
                
                return new Pair<>(JellyType.parse(functionType, typeManager).makeTypeExp(ImmutableMap.copyOf(typeVars)), typeVars);
            }
            catch (UserException | InternalException e)
            {
                // It's us that wrote the type, so user exceptions become internal exceptions:
                throw new InternalException("Error in built-in function " + functionName, e);
            }
        };
    }


    public @Localized String getMiniDescription()
    {
        return miniDescription;
    }

    // Function type, and map from named typed vars to type expression
    public Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> getType(TypeManager typeManager) throws InternalException
    {
        return typeMatcher.makeParamAndReturnType(typeManager);
    }

    /**
     * Gets the instance of this function
     * @param typeManager The TypeManager to use
     * @param paramTypes A map from the declared typevar/unitvar items in the function XML description, to the concrete Unit or DataType
     * @return The function instance to use during execution.
     */
    @OnThread(Tag.Simulation)
    public abstract ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException;
    
    /**
     * For autocompleting parameters: what are likely types to this function?
     */
    private @Nullable TypeExp getLikelyParamType()
    {
        return null; /*
        try
        {
            return typeMatcher.makeParamAndReturnType().paramType;
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return null;
        }*/
    }

    /**
     * Gets the text to display when showing information about the argument type in the GUI.
     */
    public @Localized String getParamDisplay()
    {
        @Nullable TypeExp paramType = getLikelyParamType();
        return paramType == null ? "" : paramType.toString();
    }
    
    @Override
    public ImmutableList<String> getParamNames()
    {
        return paramNames;
    }

    /**
     * Gets the text to display when showing information about the return type in the GUI.
     */
    public @Localized String getReturnDisplay()
    {
        return ""; /*
        try
        {
            return typeMatcher.makeParamAndReturnType().returnType.toString();
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return "";
        }*/
    }

    public @ExpressionIdentifier String getName()
    {
        return extractName(funcDocKey);
    }

    public String getNamespace()
    {
        return extractNamespace(funcDocKey);
    }

    public @FuncDocKey String getDocKey()
    {
        return funcDocKey;
    }

    @Override
    public ImmutableList<String> getSynonyms()
    {
        return synonyms;
    }

    public static interface TypeMatcher
    {
        // We have to make it fresh for each type inference, because the params and return may
        // share a type variable which will get unified during the inference
        // Returns type of function, and type vars by name.
        public Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> makeParamAndReturnType(TypeManager typeManager) throws InternalException;
    }

    // For testing: give a unit list and parameter list that should fail typechecking
    /*
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getTypes(1, type ->
        {
            return TypeExp.unifyTypes(typeMatcher.makeParamAndReturnType(newExpressionOfDifferentType.getTypeManager()).paramType, TypeExp.fromConcrete(null, type.get(0))).isLeft();
        }).get(0));
    }
    */
}
